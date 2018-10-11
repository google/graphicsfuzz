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

package com.graphicsfuzz.reducer.filejudge;

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.FileJudgeException;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.server.thrift.ImageComparisonMetric;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IImageFileComparator;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageShaderFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageShaderFileJudge.class);

  private IShaderDispatcher imageGenerator;
  private final boolean throwExceptionOnValidationError;
  private final ShaderJobFileOperations fileOps;
  private final File referenceShaderResultFile;
  private final IImageFileComparator fileComparator;

  public ImageShaderFileJudge(
      File referenceShaderResultFile,
      IShaderDispatcher imageGenerator,
      boolean throwExceptionOnValidationError,
      IImageFileComparator fileComparator,
      ShaderJobFileOperations fileOps) {
    this.imageGenerator = imageGenerator;
    this.throwExceptionOnValidationError = throwExceptionOnValidationError;
    this.referenceShaderResultFile = referenceShaderResultFile;
    this.fileComparator = fileComparator;
    this.fileOps = fileOps;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput) throws FileJudgeException {

    // 1. Shader files validate.
    // 2. Generate image.
    // 3. Compare.

    try {
      // 1.
      if (!fileOps.areShadersValid(shaderJobFile, throwExceptionOnValidationError)) {
        return false;
      }
      // 2.

      // Read shader job into an image job.
      ImageJob imageJob = new ImageJob();
      fileOps.readShaderJobFileToImageJob(shaderJobFile, imageJob);
      imageJob.setSkipRender(false);

      // Run the image job.
      ImageJobResult imageRes = imageGenerator.getImage(imageJob);

      // Write the shader result file.
      fileOps.writeShaderResultToFile(imageRes, shaderResultFileOutput, Optional.empty());

      switch (imageRes.getStatus()) {
        case SUCCESS:
          break;
        case SAME_AS_REFERENCE:
          throw new IllegalStateException("No longer supported: " + JobStatus.SAME_AS_REFERENCE);
        default:
          LOGGER.info("Failed to run shader. Not interesting.");
          return false;
      }

      // Success:

      // 3.

      if (!fileComparator.areFilesInteresting(referenceShaderResultFile, shaderResultFileOutput)) {
        LOGGER.info("Shader image was not deemed interesting by file comparator. Not interesting.");
        return false;
      }
      LOGGER.info("Interesting.");
      return true;
    } catch (InterruptedException | ShaderDispatchException | IOException exception) {
      LOGGER.info("Error occurred while checking if file was interesting.", exception);
      throw new FileJudgeException(exception);
    }

  }



}
