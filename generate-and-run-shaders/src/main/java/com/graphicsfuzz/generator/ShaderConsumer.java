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
import com.graphicsfuzz.common.util.ImageColorComponents;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.util.RestrictFragmentShaderColors;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderConsumer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderConsumer.class);

  private final int limit;
  private final BlockingQueue<ShaderJob> queue;
  private final File outputDir;
  private final File tempDir;
  private final String server;
  private final String worker;
  private final Set<String> crashStringsToIgnore;
  private final ShaderJobFileOperations fileOps;

  public ShaderConsumer(
      int limit,
      BlockingQueue<ShaderJob> queue,
      File outputDir,
      String server,
      String worker,
      Set<String> crashStringsToIgnore,
      ShaderJobFileOperations fileOps) {
    this.limit = limit;
    this.queue = queue;
    this.outputDir = outputDir;
    this.tempDir = new File(outputDir, "temp");
    this.server = server;
    this.worker = worker;
    this.crashStringsToIgnore = crashStringsToIgnore;
    this.fileOps = fileOps;
  }

  @Override
  public void run() {

    final IShaderDispatcher imageGenerator =
        new RemoteShaderDispatcher(server + "/manageAPI", worker);

    final File invalidDirectory = new File(outputDir, "INVALID");

    final File variantShaderJobFile = new File(tempDir, "temp_variant.json");
    final File variantShaderJobResultFile = new File(tempDir, "temp_variant.info.json");


    for (int received = 0; received < limit; received++) {
      LOGGER.info("Consuming shader job " + received);
      ShaderJob variantShaderJob;
      try {
        variantShaderJob = queue.take();
      } catch (InterruptedException exception) {
        LOGGER.error("Problem taking from queue.", exception);
        throw new RuntimeException(exception);
      }

      // Clear temp directory.
      try {
        fileOps.deleteDirectory(tempDir);
        fileOps.mkdir(tempDir);
      } catch (IOException exception) {
        LOGGER.error("Problem deleting/creating temp directory.", exception);
        throw new RuntimeException(exception);
      }

      final String counterString = String.format("%04d", received);

      final Optional<ImageJobResult> variantResult =
          runShaderJob(
              variantShaderJob,
              variantShaderJobFile,
              variantShaderJobResultFile,
              imageGenerator,
              counterString,
              invalidDirectory);

      if (!variantResult.isPresent()) {
        continue;
      }

      maybeLogFailure(
          variantResult.get(),
          variantShaderJobFile,
          variantShaderJobResultFile,
          counterString);
    }
  }

  private void maybeLogFailure(
      ImageJobResult imageJobResult,
      File shaderJobFile,
      File shaderJobResultFile,
      String counter) {
    try {
      switch (imageJobResult.getStatus()) {
        case SUCCESS:
          if (!ImageColorComponents.containsOnlyGivenComponentValues(
              fileOps.getBufferedImageFromShaderJobResultFile(shaderJobResultFile),
              Arrays.asList(RestrictFragmentShaderColors.HI_COLOR_COMPONENT_VALUE,
                  RestrictFragmentShaderColors.LO_COLOR_COMPONENT_VALUE))) {
            triageIssue(imageJobResult, shaderJobFile, shaderJobResultFile, counter);
          }
          break;
        case CRASH:
        case COMPILE_ERROR:
        case LINK_ERROR:
        case TIMEOUT:
        case UNEXPECTED_ERROR:
        case SANITY_ERROR:
        case NONDET:
          triageIssue(imageJobResult, shaderJobFile, shaderJobResultFile, counter);
          break;
        default:
          // nothing
          break;
      }
    } catch (IOException exception) {
      LOGGER.error(
          "A problem occurred when logging failures; defeats the point of the exercise.",
          exception);
      throw new RuntimeException(exception);
    }
  }

  private void triageIssue(ImageJobResult imageJobResult, File shaderJobFile,
                           File shaderJobResultFile, String counter) throws IOException {
    File triageDirectory = new File(outputDir, imageJobResult.getStatus().toString());
    fileOps.mkdir(triageDirectory);
    if (JobStatus.CRASH.equals(imageJobResult.getStatus())) {

      if (crashStringsToIgnore.stream().anyMatch(imageJobResult.getLog()::contains)) {
        triageDirectory = new File(triageDirectory, "IGNORE");
        fileOps.mkdir(triageDirectory);
      }
    }
    fileOps.copyShaderJobFileTo(
        shaderJobFile,
        new File(triageDirectory, counter + shaderJobFile.getName()),
        false);
    fileOps.copyShaderJobResultFileTo(
        shaderJobResultFile,
        new File(triageDirectory, counter + shaderJobResultFile.getName()),
        false);
  }

  private Optional<ImageJobResult> runShaderJob(ShaderJob shaderJob,
                                                File shaderJobFileTemp,
                                                File shaderJobResultFileTemp,
                                                IShaderDispatcher imageGenerator,
                                                String counterString,
                                                File invalidDirectory) {
    // shaderJob -> shaderJobFileTemp
    try {
      fileOps.writeShaderJobFile(shaderJob, shaderJobFileTemp);
    } catch (IOException exception) {
      LOGGER.error("Could not emit " + shaderJobFileTemp + " shader job.", exception);
      return Optional.empty();
    }

    // shaderJobFileTemp -> validate()
    try {
      final boolean valid = fileOps.areShadersValid(shaderJobFileTemp, false);
      if (!valid) {
        fileOps.mkdir(invalidDirectory);
        final File shaderJobFileCopyForTriage = new File(
            invalidDirectory,
            counterString + shaderJobFileTemp.getName());
        fileOps.copyShaderJobFileTo(shaderJobFileTemp, shaderJobFileCopyForTriage, true);
        return Optional.empty();
      }

    } catch (InterruptedException | IOException exception) {
      LOGGER.error("Problem validating shader job " + shaderJobFileTemp, exception);
      return Optional.empty();
    }

    // shaderJobFileTemp -> ImageJobResult [returned] + shaderJobFileResult
    try {

      return Optional.of(
          RunShaderFamily.runShader(
              shaderJobResultFileTemp,
              shaderJobFileTemp,
              imageGenerator,
              Optional.empty(),
              fileOps));
    } catch (InterruptedException | IOException | ShaderDispatchException exception) {
      LOGGER.error("Problem running shader job: " + shaderJobFileTemp, exception);
      return Optional.empty();
    }
  }


}
