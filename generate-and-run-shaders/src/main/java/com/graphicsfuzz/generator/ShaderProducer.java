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

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.tool.EnabledTransformations;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.tool.GeneratorArguments;
import com.graphicsfuzz.generator.tool.PrepareReference;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderProducer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProducer.class);

  private final int limit;
  private final File[] shaderJobFiles;
  private final IRandom generator;
  private final BlockingQueue<Pair<ShaderJob, ShaderJob>> queue;
  private final File referencesDir;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final Namespace ns;
  private final File donorsDir;
  private final ShaderJobFileOperations fileOps;

  ShaderProducer(
      int limit,
      File[] shaderJobFiles,
      IRandom generator,
      BlockingQueue<Pair<ShaderJob, ShaderJob>> queue,
      File referencesDir,
      ShadingLanguageVersion shadingLanguageVersion,
      File donorsDir,
      Namespace ns,
      ShaderJobFileOperations fileOps) {
    this.limit = limit;
    this.shaderJobFiles = shaderJobFiles;
    this.generator = generator;
    this.queue = queue;
    this.referencesDir = referencesDir;
    this.shadingLanguageVersion = shadingLanguageVersion;
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
            shadingLanguageVersion,
            generator.nextInt(Integer.MAX_VALUE),
            ns.get("small"),
            ns.get("avoid_long_loops"),
            ns.get("multi_pass"),
            ns.get("aggressively_complicate_control_flow"),
            ns.get("replace_float_literals"),
            donorsDir,
            ns.get("generate_uniform_bindings"),
            ns.get("max_uniforms"),
            enabledTransformations
        );

    int sent = 0;
    for (int counter = 0; sent < limit; counter++) {

      File referenceShaderJobFile = shaderJobFiles[counter % shaderJobFiles.length];

      try {
        LOGGER.info("Preparing shader job pair based on {}.", referenceShaderJobFile);

        final ShaderJob referenceShaderJob =
            fileOps.readShaderJobFile(referenceShaderJobFile, false);
        final ShaderJob variantShaderJob = referenceShaderJob.clone();

        PrepareReference.prepareReference(
            referenceShaderJob,
            shadingLanguageVersion,
            generatorArguments.getReplaceFloatLiterals(),
            generatorArguments.getMaxUniforms(),
            generatorArguments.getGenerateUniformBindings());
        Generate.generateVariant(variantShaderJob, generatorArguments);
        try {
          queue.put(new ImmutablePair<>(referenceShaderJob, variantShaderJob));
        } catch (InterruptedException exception) {
          LOGGER.error("Problem putting to queue.", exception);
          throw new RuntimeException(exception);
        }
        sent++;
        LOGGER.info("Sent shader job pair.");
      } catch (ParseTimeoutException | IOException | AssertionError exception) {
        // Something went wrong - log the details and move on.
        LOGGER.error("Error during generation.", exception);
        continue;
      }
    }
  }

}
