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
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageGenErrorShaderFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageGenErrorShaderFileJudge.class);

  private Pattern pattern;
  private IShaderDispatcher imageGenerator;
  private final boolean skipRender;
  private final boolean throwExceptionOnValidationError;

  public ImageGenErrorShaderFileJudge(Pattern pattern,
        IShaderDispatcher imageGenerator,
        boolean skipRender,
        boolean throwExceptionOnValidationError) {
    this.pattern = pattern;
    this.imageGenerator = imageGenerator;
    this.skipRender = skipRender;
    this.throwExceptionOnValidationError = throwExceptionOnValidationError;
  }

  @Override
  public boolean isInteresting(File workDir, String shaderJobShortName) throws FileJudgeException {

    // 1. Shader file validates.
    // 2. Generate image.

    try {
      if (!ShaderJudgeUtil.shadersAreValid(workDir,
          shaderJobShortName,
          throwExceptionOnValidationError)) {
        return false;
      }
      // 2.
      File outputImage = new File(workDir, shaderJobShortName + ".png");

      ImageJobResult imageRes = imageGenerator.getImage(shaderJobShortName, outputImage,
          skipRender);
      if (outputImage.isFile()) {
        outputImage.delete();
      }

      File outputText = new File(workDir,
          shaderJobShortName + ".txt");

      switch (imageRes.getStatus()) {
        case SUCCESS:
        case SAME_AS_REFERENCE:
          LOGGER.info("Get_image succeeded on shader. Not interesting.");
          return false;
        default:
          LOGGER.info("get_image failed...which is good.");
          FileUtils.writeStringToFile(outputText, imageRes.getLog(), Charset.defaultCharset());
          if (pattern == null) {
            LOGGER.info("Interesting.");
            return true;
          }
          if (pattern.matcher(imageRes.getLog()).matches()) {
            LOGGER.info("Regex matched. Interesting");
            return true;
          }
          LOGGER.info("Regex did not match. Not interesting");

          return false;
      }
    } catch (InterruptedException | IllegalStateException | IOException
          | ShaderDispatchException exception) {
      LOGGER.info("Error occurred while checking if file was interesting.", exception);
      throw new FileJudgeException(exception);
    }
  }
}
