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

package com.graphicsfuzz.reducer.tool;

import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.IFileJudge;
import java.io.File;
import java.io.IOException;

/**
 * Requires a shader to be valid to be interesting.
 * After that, says that the first reduction step is interesting, and thereafter decides randomly.
 */
public class RandomFileJudge implements IFileJudge {

  private boolean first;
  private final IRandom generator;
  private final int threshold;
  private final boolean throwExceptionOnInvalid;
  private final ShaderJobFileOperations fileOps;

  private static final int LIMIT = 100;

  public RandomFileJudge(
      IRandom generator,
      int threshold,
      boolean throwExceptionOnInvalid,
      ShaderJobFileOperations fileOps) {
    if (threshold < 0 || threshold >= LIMIT) {
      throw new IllegalArgumentException("Threshold must be in range [0, " + (LIMIT - 1) + "]");
    }
    this.first = true;
    this.generator = generator;
    this.threshold = threshold;
    this.throwExceptionOnInvalid = throwExceptionOnInvalid;
    this.fileOps = fileOps;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput
  ) {
    try {
      if (!fileOps.areShadersValid(shaderJobFile, throwExceptionOnInvalid)) {
        return false;
      }
    } catch (IOException | InterruptedException ex) {
      throw new RuntimeException(ex);
    }
    if (first) {
      first = false;
      return true;
    }
    return generator.nextInt(LIMIT) < threshold;
  }

}
