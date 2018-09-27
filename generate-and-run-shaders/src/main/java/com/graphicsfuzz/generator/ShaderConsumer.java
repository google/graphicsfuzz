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
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.generator.tool.PrepareReference;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ImageData;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.shadersets.RunShaderSet;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class ShaderConsumer implements Runnable {

  private final int limit;
  private final BlockingQueue<ReferenceVariantPair> queue;
  private final File outputDir;
  private final String server;
  private final String token;
  private final List<File> referenceShaders;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final boolean replaceFloatLiterals;

  public ShaderConsumer(
        int limit,
        BlockingQueue<ReferenceVariantPair> queue,
        File outputDir,
        String server,
        String token,
        List<File> referenceShaders,
        ShadingLanguageVersion shadingLanguageVersion,
        boolean replaceFloatLiterals) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.server = server;
    this.token = token;
    this.referenceShaders = referenceShaders;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.replaceFloatLiterals = replaceFloatLiterals;
  }

  @Override
  public void run() {

    try {

      final IShaderDispatcher imageGenerator = new RemoteShaderDispatcher(
            server + "/manageAPI",
            token);
      final Map<File, ImageData> referenceToImageData = new HashMap<>();
      final Map<File, File> referenceShaderToPreparedReferenceShader =
            prepareReferenceShaders(referenceShaders, outputDir,
                shadingLanguageVersion, replaceFloatLiterals);

      for (File reference : referenceShaders) {
        final File preparedReferenceShader =
              referenceShaderToPreparedReferenceShader.get(reference);
        final ImageJobResult referenceResult =
              RunShaderSet.runShader(
                    preparedReferenceShader,
                    outputDir,
                    imageGenerator,
                    Optional.empty());
        if (referenceResult.status != JobStatus.SUCCESS) {
          throw new RuntimeException("Error rendering reference shader "
                + preparedReferenceShader.getAbsolutePath());
        }
        final File referenceImage = new File(
              FilenameUtils.removeExtension(preparedReferenceShader.getAbsolutePath()) + ".png");
        assert referenceImage.exists();
        referenceToImageData.put(reference,
              new ImageData(referenceImage));
      }

      for (int received = 0; received < limit; received++) {
        final ReferenceVariantPair generatedShader = queue.take();
        final String outputFilenamePrefix
              = FilenameUtils.removeExtension(generatedShader.getVariant().getName());
        final ExecResult execResult = ToolHelper.runValidatorOnShader(
              RedirectType.TO_BUFFER, generatedShader.getVariant());
        if (execResult.res != 0) {
          FileUtils.moveFile(generatedShader.getVariant(),
                new File(outputDir, "invalid_" + outputFilenamePrefix + ".frag"));
          continue;
        }
        RunShaderSet.runShader(generatedShader.getVariant(),
              outputDir,
              imageGenerator,
              Optional.of(referenceToImageData.get(generatedShader.getReference())));
      }
    } catch (InterruptedException | IOException | ShaderDispatchException
          | ParseTimeoutException exception) {
      throw new RuntimeException(exception);
    }
  }

  private static Map<File, File> prepareReferenceShaders(List<File> references,
        File outputDir,
        ShadingLanguageVersion shadingLanguageVersion,
        boolean replaceFloatLiterals) throws IOException, ParseTimeoutException {
    final Map<File, File> result = new HashMap<>();
    for (File reference : references) {
      final String referencePrefix = FilenameUtils.removeExtension(
          reference.getAbsolutePath());
      final File uniforms = new File(referencePrefix + ".json");
      final File license = new File(referencePrefix + ".license");
      final String outputPrefix = FilenameUtils.getBaseName(reference.getName());
      PrepareReference.prepareReference(
          referencePrefix,
          outputDir,
          outputPrefix,
          shadingLanguageVersion,
          replaceFloatLiterals,
          0,
          false);
      final File outputFile = new File(outputDir, outputPrefix + ".frag");
      result.put(reference, outputFile);
      FileUtils.copyFile(license, new File(outputDir,
            FilenameUtils.removeExtension(reference.getName()) + ".license"));
    }
    return result;
  }

}
