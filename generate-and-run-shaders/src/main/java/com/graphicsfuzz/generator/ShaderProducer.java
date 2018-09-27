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
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.tool.EnabledTransformations;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.tool.GeneratorArguments;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class ShaderProducer implements Runnable {

  private final int limit;
  private final BlockingQueue<ReferenceVariantPair> queue;
  private final File outputDir;
  private final List<File> referenceFragmentShaders;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final boolean replaceFloatLiterals;
  private final Namespace ns;
  private final File donors;

  public ShaderProducer(
        int limit,
        BlockingQueue<ReferenceVariantPair> queue,
        File outputDir,
        List<File> referenceFragmentShaders,
        ShadingLanguageVersion shadingLanguageVersion,
        boolean replaceFloatLiterals,
        File donors,
        Namespace ns) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.referenceFragmentShaders = referenceFragmentShaders;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.replaceFloatLiterals = replaceFloatLiterals;
    this.donors = donors;
    this.ns = ns;
  }

  @Override
  public void run() {
    try {
      final IRandom generator = new RandomWrapper(ns.get("seed"));

      final EnabledTransformations enabledTransformations =
            Generate.getTransformationDisablingFlags(ns);

      int sent = 0;
      int counter = 0;
      while (sent < limit) {
        final int index = counter++;
        final File referenceFragment = referenceFragmentShaders
            .get(index % referenceFragmentShaders.size());
        final String outputFilenamePrefix = String.format("%04d", index)
              + "_" + FilenameUtils.getBaseName(referenceFragment.getName());

        final GeneratorArguments generatorArguments =
              new GeneratorArguments(shadingLanguageVersion,
                  generator.nextInt(Integer.MAX_VALUE),
                  ns.getBoolean("small"),
                  ns.getBoolean("avoid_long_loops"),
                  ns.getBoolean("multi_pass"),
                  ns.getBoolean("aggressively_complicate_control_flow"),
                  replaceFloatLiterals,
                  donors,
                  enabledTransformations
              );

        try {
          final File referenceJsonFile =
              new File(FilenameUtils.removeExtension(referenceFragment.getAbsolutePath())
                  + ".json");
          final ShaderJob shaderJob = new GlslShaderJob(
              Optional.empty(),
              Optional.of(Helper.parse(
                  new File(referenceFragment.getAbsolutePath()), false)),
              new UniformsInfo(referenceJsonFile));
          Generate.generateVariant(shaderJob, generatorArguments);
          Helper.emitShaderJob(shaderJob, shadingLanguageVersion, outputFilenamePrefix,
              outputDir, null);
        } catch (Exception | AssertionError exception) {
          // Something went wrong - grab the details and move on.
          FileUtils.writeStringToFile(
                new File(outputDir, outputFilenamePrefix + ".error"),
                exception.toString(), StandardCharsets.UTF_8);
          continue;
        }
        final File generatedShader = new File(outputDir, outputFilenamePrefix + ".frag");
        assert generatedShader.exists();
        final File generatedShaderFlags = new File(FilenameUtils.removeExtension(generatedShader
              .getAbsolutePath()) + ".flags");
        FileUtils.writeStringToFile(generatedShaderFlags, generatorArguments.toString(),
              StandardCharsets.UTF_8);
        queue.put(new ReferenceVariantPair(referenceFragment, generatedShader));
        sent++;
      }
    } catch (Throwable exception) {
      throw new RuntimeException(exception);
    }
  }
}
