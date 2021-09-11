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

  // What sort of shader are we generating?
  private final ShaderKind shaderKind;

  private final boolean isWgslCompatible;

  // Can we rely in 'injectionSwitch' being defined?
  private final boolean injectionSwitchIsAvailable;

  // Expression fuzzing and identity transformations
  private int maxDepthForGeneratedExpr = 3;

  // Structification
  private int maxStructNestingDepth = 2;
  private int maxStructFields = 8;

  // The maximum number of distinct donors that can be used during one donation pass.
  private int maxDonorsPerDonationPass = 4;

  // Adding switch statements
  private int maxInjectedSwitchCasesBeforeOriginalCode = 3;
  private int maxInjectedSwitchCasesInOriginalCode = 10;
  private int maxInjectedSwitchCasesAfterOriginalCode = 3;

  private GenerationParams(ShaderKind shaderKind, boolean isWgslCompatible,
                           boolean injectionSwitchIsAvailable) {
    // Prevent external construction
    this.shaderKind = shaderKind;
    this.isWgslCompatible = isWgslCompatible;
    this.injectionSwitchIsAvailable = injectionSwitchIsAvailable;
  }

  public static GenerationParams normal(ShaderKind shaderKind,
                                        boolean isWgslCompatible,
                                        boolean injectionSwitchAvailable) {
    final GenerationParams result = new GenerationParams(shaderKind, isWgslCompatible,
        injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 3;
    result.maxStructNestingDepth = 2;
    result.maxStructFields = 8;
    result.maxDonorsPerDonationPass = 4;
    result.maxInjectedSwitchCasesBeforeOriginalCode = 3;
    result.maxInjectedSwitchCasesInOriginalCode = 10;
    result.maxInjectedSwitchCasesAfterOriginalCode = 3;
    return result;
  }

  public static GenerationParams small(ShaderKind shaderKind,
                                       boolean isWgslCompatible,
                                       boolean injectionSwitchAvailable) {
    GenerationParams result = new GenerationParams(shaderKind, isWgslCompatible,
        injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 2;
    result.maxStructNestingDepth = 1;
    result.maxStructFields = 3;
    result.maxDonorsPerDonationPass = 2;
    result.maxInjectedSwitchCasesBeforeOriginalCode = 2;
    result.maxInjectedSwitchCasesInOriginalCode = 4;
    result.maxInjectedSwitchCasesAfterOriginalCode = 2;
    return result;
  }

  public static GenerationParams large(ShaderKind shaderKind,
                                       boolean isWgslCompatible,
                                       boolean injectionSwitchAvailable) {
    GenerationParams result = new GenerationParams(shaderKind, isWgslCompatible,
        injectionSwitchAvailable);
    result.maxDepthForGeneratedExpr = 5;
    result.maxStructNestingDepth = 4;
    result.maxStructFields = 7;
    result.maxDonorsPerDonationPass = 6;
    result.maxInjectedSwitchCasesBeforeOriginalCode = 6;
    result.maxInjectedSwitchCasesInOriginalCode = 20;
    result.maxInjectedSwitchCasesAfterOriginalCode = 6;
    return result;
  }

  public ShaderKind getShaderKind() {
    return shaderKind;
  }

  public boolean isWgslCompatible() {
    return isWgslCompatible;
  }

  public boolean getInjectionSwitchIsAvailable() {
    return injectionSwitchIsAvailable;
  }

  public int getMaxDepthForGeneratedExpr() {
    return maxDepthForGeneratedExpr;
  }

  public int getMaxStructNestingDepth() {
    return maxStructNestingDepth;
  }

  public int getMaxStructFields() {
    return maxStructFields;
  }

  public int getMaxDonorsPerDonationPass() {
    return maxDonorsPerDonationPass;
  }

  public int getMaxInjectedSwitchCasesBeforeOriginalCode() {
    return maxInjectedSwitchCasesBeforeOriginalCode;
  }

  public int getMaxInjectedSwitchCasesInOriginalCode() {
    return maxInjectedSwitchCasesInOriginalCode;
  }

  public int getMaxInjectedSwitchCasesAfterOriginalCode() {
    return maxInjectedSwitchCasesAfterOriginalCode;
  }

  // For purposes of testing, it can be useful to be able to set a specific parameter to a large
  // value.  Add additional setter methods as needed.

  public void setMaxInjectedSwitchCasesAfterOriginalCode(
      int maxInjectedSwitchCasesAfterOriginalCode) {
    this.maxInjectedSwitchCasesAfterOriginalCode = maxInjectedSwitchCasesAfterOriginalCode;
  }

}
