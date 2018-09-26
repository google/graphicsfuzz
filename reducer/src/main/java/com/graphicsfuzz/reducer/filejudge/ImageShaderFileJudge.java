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

import com.graphicsfuzz.reducer.FileJudgeException;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.reducer.util.ShaderJudgeUtil;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.shadersets.IImageFileComparator;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageShaderFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageShaderFileJudge.class);

  private static final String REFERENCE_IMAGE_NAME = "reference_image.png";

  private File workDir;
  private IImageFileComparator fileComparator;
  private IShaderDispatcher imageGenerator;
  private final boolean throwExceptionOnValidationError;

  public ImageShaderFileJudge(
        File workDir,
        IImageFileComparator fileComparator,
        IShaderDispatcher imageGenerator,
        boolean throwExceptionOnValidationError) {
    this.workDir = workDir;
    this.fileComparator = fileComparator;
    this.imageGenerator = imageGenerator;
    this.throwExceptionOnValidationError = throwExceptionOnValidationError;
  }

  public static File getReferenceImageInWorkDir(File workDir) {
    return Paths.get(workDir.toString(), REFERENCE_IMAGE_NAME).toFile();
  }

  @Override
  public boolean isInteresting(String filesPrefix) throws FileJudgeException {

    // 1. Shader files validate.
    // 2. Generate image.
    // 3. Compare.

    try {
      // 1.
      if (!ShaderJudgeUtil.shadersAreValid(filesPrefix, throwExceptionOnValidationError)) {
        return false;
      }
      // 2.

      File outputImage = new File(workDir, FilenameUtils.getBaseName(filesPrefix) + ".png");
      File outputText = new File(workDir, FilenameUtils.getBaseName(filesPrefix) + ".txt");
      ImageJobResult imageRes = imageGenerator
            .getImage(filesPrefix, outputImage, false);

      switch (imageRes.getStatus()) {
        case SUCCESS:
          if (imageRes.isSetPNG()) {
            FileUtils.writeByteArrayToFile(outputImage, imageRes.getPNG());
          }
          break;
        case SAME_AS_REFERENCE:
          FileUtils.writeStringToFile(
                outputText,
                JobStatus.SAME_AS_REFERENCE.toString() + "\n",
                StandardCharsets.UTF_8);
          outputImage = getReferenceImageInWorkDir(workDir);
          break;
        default:
          LOGGER.info("Failed to run get_image on shader. Not interesting.");
          FileUtils.writeStringToFile(outputText, imageRes.getLog(),
              StandardCharsets.UTF_8);
          return false;
      }

      // Success or same as ref:

      // 3.
      if (!fileComparator.areFilesInteresting(getReferenceImageInWorkDir(workDir), outputImage)) {
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
