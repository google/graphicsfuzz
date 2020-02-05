/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.transformreduce.ShaderJob;

public class GloballyTruncateLoops {

  /**
   * Adds logic to limit the number of iterations that each shader in a given shader job can
   * execute.
   * - A global constant, loopBoundName, is declared and initialized to loopLimit.
   * - A global variable, loopCountName, is declared and initialized to 0.
   * - For each loop whose guard is not literally 'false':
   *   - The loop guard 'e' is changed to '(e) && (loopBoundName < loopLimiterName)'
   *   - A statement 'loopCountName++' is added at the start of the body of the loop
   *   - The loop body is changed from a single statement to a block, if needed, to
   *     accommodate this.
   * @param shaderJob A shader job to be mutated.
   * @param loopLimit The maximum number of loop iterations that any shader in the shader job
   *                  should be allowed to make.
   * @param loopCountName The name of a global variable that will be added to each shader in the
   *                        shader job to store the number of loop iterations executed so far.
   * @param loopBoundName The name of a global constant that will be set to loopLimit.
   */
  public static void truncate(ShaderJob shaderJob, int loopLimit, String loopCountName,
                              String loopBoundName) {
    // TODO(https://github.com/google/graphicsfuzz/issues/866): Implement loop limiting
    //  logic.
    throw new RuntimeException("Not implemented yet.");
  }

}
