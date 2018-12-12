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
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageGenErrorShaderFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenErrorShaderFileJudge.class);

  private String errorString;
  private IShaderDispatcher imageGenerator;
  private final boolean skipRender;
  private final boolean throwExceptionOnValidationError;
  private final ShaderJobFileOperations fileOps;

  public ImageGenErrorShaderFileJudge(
      String errorString,
      IShaderDispatcher imageGenerator,
      boolean skipRender,
      boolean throwExceptionOnValidationError,
      ShaderJobFileOperations fileOps) {
    this.errorString = errorString;
    this.imageGenerator = imageGenerator;
    this.skipRender = skipRender;
    this.throwExceptionOnValidationError = throwExceptionOnValidationError;
    this.fileOps = fileOps;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput
  ) throws FileJudgeException {

    // 1. Shader file validates.
    // 2. Generate image.

    try {
      if (!fileOps.areShadersValid(shaderJobFile, throwExceptionOnValidationError)) {
        return false;
      }

      // 2.
      // Read shader job into an image job.
      ImageJob imageJob = new ImageJob();
      fileOps.readShaderJobFileToImageJob(shaderJobFile, imageJob);
      imageJob.setSkipRender(skipRender);

      // Run the image job.
      ImageJobResult imageRes = imageGenerator.getImage(imageJob);

      // Write the shader result file.
      fileOps.writeShaderResultToFile(imageRes, shaderResultFileOutput, Optional.empty());

      switch (imageRes.getStatus()) {
        case SUCCESS:
        case SAME_AS_REFERENCE:
          LOGGER.info("Get_image succeeded on shader. Not interesting.");
          return false;
        default:
          LOGGER.info("get_image failed...which is good.");
          if (errorString == null) {
            LOGGER.info("Interesting.");
            return true;
          }
          if (imageRes.getLog().contains(errorString)) {
            LOGGER.info("Error string was found. Interesting");
            return true;
          }
          LOGGER.info("Error string was not found. Not interesting");

          return false;
      }
    } catch (InterruptedException | IllegalStateException | IOException
          | ShaderDispatchException exception) {
      LOGGER.error("Error occurred while checking if file was interesting.", exception);
      throw new FileJudgeException(exception);
    }
  }
}
