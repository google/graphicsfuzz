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

package com.graphicsfuzz.common.glslversion;

final class Essl100 implements ShadingLanguageVersion {

  static final ShadingLanguageVersion INSTANCE = new Essl100();

  private Essl100() {
    // Singleton
  }

  @Override
  public String getVersionString() {
    return "100";
  }

  @Override
  public boolean globalVariableInitializersMustBeConst() {
    return true;
  }

  @Override
  public boolean initializersOfConstMustBeConst() {
    return true;
  }

  @Override
  public boolean isEssl() {
    return true;
  }

  @Override
  public boolean isWebGl() {
    return false;
  }

  @Override
  public boolean supportedArrayConstructors() {
    return false;
  }

  @Override
  public boolean restrictedForLoops() {
    return true;
  }

  @Override
  public boolean supportedAbsInt() {
    return false;
  }

  @Override
  public boolean supportedBitwiseOperations() {
    return false;
  }

  @Override
  public boolean supportedClampInt() {
    return false;
  }

  @Override
  public boolean supportedClampUint() {
    return false;
  }

  @Override
  public boolean supportedComputeShaders() {
    return false;
  }

  @Override
  public boolean supportedDerivativeFunctions() {
    return false;
  }

  @Override
  public boolean supportedDeterminant() {
    return false;
  }

  @Override
  public boolean supportedDoStmt() {
    return true;
  }

  @Override
  public boolean supportedExplicitDerivativeFunctions() {
    return false;
  }

  @Override
  public boolean supportedFloatBitsToInt() {
    return false;
  }

  @Override
  public boolean supportedFloatBitsToUint() {
    return false;
  }

  @Override
  public boolean supportedFma() {
    return false;
  }

  @Override
  public boolean supportedGlFragColor() {
    return true;
  }

  @Override
  public boolean supportedIntBitsToFloat() {
    return false;
  }

  @Override
  public boolean supportedIntegerFunctions() {
    return false;
  }

  @Override
  public boolean supportedInterpolationFunctions() {
    return false;
  }

  @Override
  public boolean supportedInverse() {
    return false;
  }

  @Override
  public boolean supportedIsinf() {
    return false;
  }

  @Override
  public boolean supportedIsnan() {
    return false;
  }

  @Override
  public boolean supportedMatrixCompMultNonSquare() {
    return false;
  }

  @Override
  public boolean supportedMaxInt() {
    return false;
  }

  @Override
  public boolean supportedMaxUint() {
    return false;
  }

  @Override
  public boolean supportedMinInt() {
    return false;
  }

  @Override
  public boolean supportedMinUint() {
    return false;
  }

  @Override
  public boolean supportedMixFloatBool() {
    return false;
  }

  @Override
  public boolean supportedMixNonfloatBool() {
    return false;
  }

  @Override
  public boolean supportedNonSquareMatrices() {
    return false;
  }

  @Override
  public boolean supportedOuterProduct() {
    return false;
  }

  @Override
  public boolean supportedPackHalf2x16() {
    return false;
  }

  @Override
  public boolean supportedPackSnorm2x16() {
    return false;
  }

  @Override
  public boolean supportedPackSnorm4x8() {
    return false;
  }

  @Override
  public boolean supportedPackUnorm2x16() {
    return false;
  }

  @Override
  public boolean supportedPackUnorm4x8() {
    return false;
  }

  @Override
  public boolean supportedRound() {
    return false;
  }

  @Override
  public boolean supportedRoundEven() {
    return false;
  }

  @Override
  public boolean supportedSignInt() {
    return false;
  }

  @Override
  public boolean supportedSwitchStmt() {
    return false;
  }

  @Override
  public boolean supportedTranspose() {
    return false;
  }

  @Override
  public boolean supportedTrunc() {
    return false;
  }

  @Override
  public boolean supportedUintBitsToFloat() {
    return false;
  }

  @Override
  public boolean supportedUnpackHalf2x16() {
    return false;
  }

  @Override
  public boolean supportedUnpackSnorm2x16() {
    return false;
  }

  @Override
  public boolean supportedUnpackSnorm4x8() {
    return false;
  }

  @Override
  public boolean supportedUnpackUnorm2x16() {
    return false;
  }

  @Override
  public boolean supportedUnpackUnorm4x8() {
    return false;
  }

  @Override
  public boolean supportedUnsigned() {
    return false;
  }

  @Override
  public boolean supportedAngleAndTrigonometricFunctions() {
    return true;
  }

  @Override
  public boolean supportedHyperbolicAngleAndTrigonometricFunctions() {
    return false;
  }

  @Override
  public boolean supportedModf() {
    return false;
  }

  @Override
  public boolean supportedFrexp() {
    return false;
  }

  @Override
  public boolean supportedLdexp() {
    return false;
  }

  @Override
  public boolean supportedAtomicMemoryFunctions() {
    return false;
  }

  @Override
  public boolean supportedTexture() {
    return false;
  }

  @Override
  public boolean supportedShaderInvocationControlFunctions() {
    return false;
  }

  @Override
  public boolean supportedShaderMemoryControlFunctions() {
    return false;
  }

  @Override
  public boolean supportedPushConstants() {
    return false;
  }

  @Override
  public boolean supportedScalarSwizzle() {
    return false;
  }
}
