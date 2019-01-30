/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.generator;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.tool.EnabledTransformations;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.tool.GeneratorArguments;
import com.graphicsfuzz.generator.transformation.RestrictFragmentShaderColors;
import com.graphicsfuzz.generator.transformation.injection.RemoveDiscardStatements;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.ArgsUtil;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderProducer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProducer.class);

  private final int limit;
  private final File[] shaderJobFiles;
  private final BlockingQueue<ShaderJob> queue;
  private final File referencesDir;
  private final Namespace ns;
  private final File donorsDir;
  private final ShaderJobFileOperations fileOps;

  ShaderProducer(
      int limit,
      File[] shaderJobFiles,
      BlockingQueue<ShaderJob> queue,
      File referencesDir,
      File donorsDir,
      Namespace ns,
      ShaderJobFileOperations fileOps) {
    this.limit = limit;
    this.shaderJobFiles = shaderJobFiles;
    this.queue = queue;
    this.referencesDir = referencesDir;
    this.donorsDir = donorsDir;
    this.ns = ns;
    this.fileOps = fileOps;
  }

  @Override
  public void run() {
    assert referencesDir.isDirectory();
    assert donorsDir.isDirectory();

    final EnabledTransformations enabledTransformations =
        Generate.getTransformationDisablingFlags(ns);

    final GeneratorArguments generatorArguments =
        new GeneratorArguments(
            ns.get("small"),
            ns.get("allow_long_loops"),
            ns.get("single_pass"),
            ns.get("aggressively_complicate_control_flow"),
            ns.get("replace_float_literals"),
            donorsDir,
            ns.get("generate_uniform_bindings"),
            ns.get("max_uniforms"),
            enabledTransformations,
            !ns.getBoolean("no_injection_switch")
        );

    final int outerSeed = ArgsUtil.getSeedArgument(ns);
    final IRandom generator = new RandomWrapper(outerSeed);

    int sent = 0;
    for (int counter = 0; sent < limit; counter++) {

      File referenceShaderJobFile = shaderJobFiles[counter % shaderJobFiles.length];

      try {
        LOGGER.info("Preparing variant shader job based on {}.", referenceShaderJobFile);
        final ShaderJob shaderJob =
            fileOps.readShaderJobFile(referenceShaderJobFile);

        // Remove discard statements from the original shader.  We are going to hijack the colors
        // the shader can write, and we want to be able to regard absence of a color as a bug.
        new RemoveDiscardStatements(shaderJob.getFragmentShader().get());

        // Create a variant.
        Generate.generateVariant(shaderJob, generatorArguments,
            generator.nextInt(Integer.MAX_VALUE));

        // Restrict the colors that the variant can emit.
        final float probabilityOfAddingNewColorWrite = 0.01f;
        if (!RestrictFragmentShaderColors.restrictFragmentShaderColors(shaderJob, generator,
            Constants.GLF_COLOR, GenerationParams.normal(ShaderKind.FRAGMENT,
                generatorArguments.getAddInjectionSwitch()),
            probabilityOfAddingNewColorWrite)) {
          LOGGER.info("Skipping variant as fragment shader colors could not be restricted.");
          continue;
        }
        try {
          queue.put(shaderJob);
        } catch (InterruptedException exception) {
          LOGGER.error("Problem putting to queue.", exception);
          throw new RuntimeException(exception);
        }
        LOGGER.info("Sent shader job " + sent + ".");
        sent++;
      } catch (ParseTimeoutException | IOException | AssertionError | InterruptedException
          | GlslParserException exception) {
        // Something went wrong - log the details and move on.
        LOGGER.error("Error during generation.", exception);
      }
    }
  }

}
