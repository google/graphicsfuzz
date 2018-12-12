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
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidatorErrorShaderFileJudge implements IFileJudge {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValidatorErrorShaderFileJudge.class);

  private String errorString;

  public ValidatorErrorShaderFileJudge(String errorString) {
    this.errorString = errorString;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput
  ) {

    throw new RuntimeException();

    /*
    // 1. Shader file validates.

    try {
      // 1.
      ExecResult res = ToolHelper.runValidatorOnShader(RedirectType.TO_LOG, file);
      if (res.res == 0) {
        LOGGER.info("Shader validated. Not interesting.");
        return false;
      }

      LOGGER.info("Shader failed to validate...which is good.");

      if (errorString == null) {
        LOGGER.info("Interesting.");
        return true;
      }

      if (res.stdout.contains(errorString)) {
        LOGGER.info("Error string was found. Interesting");
        return true;
      }

      if (res.stderr.contains(errorString)) {
        LOGGER.info("Error string was found. Interesting");
        return true;
      }

      LOGGER.info("Error string was not found. Not interesting");

      return false;
    } catch (InterruptedException | IOException exception) {
      LOGGER.info("Error occurred while checking if file was interesting.", exception);
      throw new FileJudgeException(exception);
    }
    */
  }
}
