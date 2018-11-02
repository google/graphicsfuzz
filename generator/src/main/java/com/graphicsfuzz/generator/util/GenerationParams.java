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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.util.ShaderKind;

public class GenerationParams {

  // Expression fuzzing
  public static final boolean COMMA_OPERATOR_ENABLED = false;
  public static final boolean NON_SQUARE_MATRICES_ENABLED = true;

  public static GenerationParams small(ShaderKind shaderKind, boolean injectionSwitchAvailable) {
    GenerationParams result = new GenerationParams(shaderKind, injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 2;
    result.maxStructNestingDepth = 1;
    result.maxStructFields = 3;
    result.maxDonors = 2;
    return result;
  }

  public static GenerationParams normal(ShaderKind shaderKind, boolean injectionSwitchAvailable) {
    GenerationParams result = new GenerationParams(shaderKind, injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 3;
    result.maxStructNestingDepth = 2;
    result.maxStructFields = 5;
    result.maxDonors = 4;
    return result;
  }

  public static GenerationParams large(ShaderKind shaderKind, boolean injectionSwitchAvailable) {
    GenerationParams result = new GenerationParams(shaderKind, injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 5;
    result.maxStructNestingDepth = 4;
    result.maxStructFields = 7;
    result.maxDonors = 6;
    return result;
  }

  private GenerationParams(ShaderKind shaderKind, boolean injectionSwitchIsAvailable) {
    // Prevent external construction
    this.shaderKind = shaderKind;
    this.injectionSwitchIsAvailable = injectionSwitchIsAvailable;
  }

  // What sort of shader are we generating?
  private final ShaderKind shaderKind;

  // Expression fuzzing and identity transformations
  private int maxDepthForGeneratedExpr = 3;

  // Structification
  private int maxStructNestingDepth = 3;
  private int maxStructFields = 8;

  // Donors
  private int maxDonors = 5;

  private final boolean injectionSwitchIsAvailable;

  public int getMaxDepthForGeneratedExpr() {
    return maxDepthForGeneratedExpr;
  }

  public int getMaxStructNestingDepth() {
    return maxStructNestingDepth;
  }

  public int getMaxStructFields() {
    return maxStructFields;
  }

  public int getMaxDonors() {
    return maxDonors;
  }

  public ShaderKind getShaderKind() {
    return shaderKind;
  }

  public boolean getInjectionSwitchIsAvailable() {
    return injectionSwitchIsAvailable;
  }

}
