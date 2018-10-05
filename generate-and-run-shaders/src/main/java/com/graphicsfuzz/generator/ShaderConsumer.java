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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderConsumer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderConsumer.class);

  private final int limit;
  private final BlockingQueue<Pair<ShaderJob, ShaderJob>> queue;
  private final File outputDir;
  private final String server;
  private final String token;
  private final ShadingLanguageVersion shadingLanguageVersion;

  public ShaderConsumer(
      int limit,
      BlockingQueue<Pair<ShaderJob, ShaderJob>> queue,
      File outputDir,
      String server,
      String token,
      ShadingLanguageVersion shadingLanguageVersion) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.server = server;
    this.token = token;
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  @Override
  public void run() {

    final IShaderDispatcher imageGenerator = new RemoteShaderDispatcher(
        server + "/manageAPI",
        token);

    for (int received = 0; received < limit; received++) {
      LOGGER.info("Consuming shader job " + received);
      Pair<ShaderJob, ShaderJob> shaderPair;
      try {
        shaderPair = queue.take();
      } catch (InterruptedException exception) {
        LOGGER.error("Problem taking from queue.", exception);
        throw new RuntimeException(exception);
      }

      final String referencePrefix = "temp_reference";
      final String variantPrefix = "temp_variant";

      final File[] files = outputDir.listFiles((dir, name) -> name.startsWith(referencePrefix)
          || name.startsWith(variantPrefix));
      for (File toDelete :
          files) {
        if (!toDelete.delete()) {
          final String message = "Problem deleting file " + toDelete.getName();
          LOGGER.error(message);
          throw new RuntimeException(message);
        }
      }

      final ShaderJob referenceShaderJob = shaderPair.getLeft();
      final Optional<ImageJobResult> referenceResult = runShaderJob(referenceShaderJob,
          referencePrefix, imageGenerator);
      if (!referenceResult.isPresent()) {
        continue;
      }
      final ShaderJob variantShaderJob = shaderPair.getRight();
      final Optional<ImageJobResult> variantResult = runShaderJob(variantShaderJob,
          variantPrefix, imageGenerator);
      if (!variantResult.isPresent()) {
        continue;
      }

      final String counterString = String.format("%04d", received);

      try {
        maybeLogFailure(referenceResult.get(), referencePrefix, counterString);
        maybeLogFailure(variantResult.get(), variantPrefix, counterString);
        maybeLogWrongImage(referenceResult.get(),
            variantResult.get(),
            referencePrefix,
            variantPrefix,
            counterString);
      } catch (IOException exception) {
        LOGGER.error(
            "A problem occurred when logging failures; defeats the point of the exercise.",
            exception);
        throw new RuntimeException(exception);
      }
    }
  }

  private void maybeLogFailure(ImageJobResult shaderJobResult, String prefix, String counter)
      throws IOException {
    switch (shaderJobResult.getStatus()) {
      case CRASH:
      case COMPILE_ERROR:
      case LINK_ERROR:
      case TIMEOUT:
      case UNEXPECTED_ERROR:
      case SANITY_ERROR:
      case NONDET:
        final File triageDirectory = new File(outputDir, shaderJobResult.getStatus().toString());
        if (!triageDirectory.exists()) {
          triageDirectory.mkdir();
          throw new RuntimeException();
        } else {
          assert triageDirectory.isDirectory();
        }
        for (File file : outputDir.listFiles((dir, name) -> name.startsWith(prefix))) {
          FileUtils.copyFile(file, new File(triageDirectory, counter + file.getName()));
        }
        return;
      default:
        return;
    }
  }

  private void maybeLogWrongImage(ImageJobResult referenceResult,
                                  ImageJobResult variantResult,
                                  String referencePrefix,
                                  String variantPrefix,
                                  String counter) {
    // TODO: implement.
  }

  private Optional<ImageJobResult> runShaderJob(ShaderJob shaderJob, String prefix,
                               IShaderDispatcher imageGenerator) {
    // Emit the shader job.
    try {
      Helper.emitShaderJob(shaderJob, shadingLanguageVersion,
          prefix,
          outputDir,
          null);
    } catch (IOException exception) {
      LOGGER.error("Could not emit " + prefix + " shader job.",
          exception);
      return Optional.empty();
    }

    // Validate the shader job.
    try {
      if (shaderJob.hasVertexShader() && !validShader(prefix + ".vert")) {
        return Optional.empty();
      }
      if (shaderJob.hasFragmentShader() && !validShader(prefix + ".frag")) {
        return Optional.empty();
      }
    } catch (InterruptedException | IOException exception) {
      LOGGER.error("Problem validating " + prefix + " shader job.", exception);
      return Optional.empty();
    }

    // Run the shader job.
    try {
      return Optional.of(RunShaderFamily.runShader(outputDir,
          prefix,
          imageGenerator,
          Optional.empty()));
    } catch (InterruptedException | IOException | ShaderDispatchException exception) {
      LOGGER.error("Problem running " + prefix + " shader job.", exception);
      return Optional.empty();
    }
  }

  private boolean validShader(String filename) throws IOException, InterruptedException {
    final ExecResult validatorResult =
        ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER,
            new File(outputDir, filename));
    if (validatorResult.res != 0) {
      LOGGER.error("Shader job " + filename + " is not valid: "
          + validatorResult.stdout + validatorResult.stderr);
      return false;
    }
    return true;
  }

}
