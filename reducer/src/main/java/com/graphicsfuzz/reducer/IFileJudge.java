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

package com.graphicsfuzz.reducer;

import java.io.File;

public interface IFileJudge {

  /**
   * @param shaderJobFile          The shader job file that will be tested.
   * @param shaderResultFileOutput Optional file where the result will be *written* in order to
   *                               determine if the shaderJobFile is interesting. Of course, the
   *                               result may not be written, as the isInteresting test may not
   *                               require running the shader at all; e.g., if isInteresting just
   *                               requires a shader to be valid according to glslangValidator.
   */
  boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput) throws FileJudgeException;

}
