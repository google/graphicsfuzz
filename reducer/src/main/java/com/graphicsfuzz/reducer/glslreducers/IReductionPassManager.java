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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.Optional;

public interface IReductionPassManager {

  /**
   * Uses the managed passes to attempt to produce a simpler shader job from the given shader job.
   * @param shaderJob The shader job to be reduced.
   * @return Empty if the reduction passes have nothing left to try, otherwise a transformed shader
   *     job.
   */
  Optional<ShaderJob> applyReduction(ShaderJob shaderJob);

  /**
   * Notify the pass manager whether the last reduction it applied turned out to be interesting.
   * @param isInteresting True if and only if the last reduction applied by the pass manager
   *                      turned out to be interesting.
   */
  void notifyInteresting(boolean isInteresting);

}
