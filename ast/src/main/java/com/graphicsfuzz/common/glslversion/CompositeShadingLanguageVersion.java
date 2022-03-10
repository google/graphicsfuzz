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

abstract class CompositeShadingLanguageVersion implements ShadingLanguageVersion {

  private final ShadingLanguageVersion prototype;

  CompositeShadingLanguageVersion(
      ShadingLanguageVersion prototype) {
    this.prototype = prototype;
  }

  @Override
  public abstract String getVersionString();

  @Override
  public boolean globalVariableInitializersMustBeConst() {
    return prototype.globalVariableInitializersMustBeConst();
  }

  @Override
  public boolean initializersOfConstMustBeConst() {
    return prototype.initializersOfConstMustBeConst();
  }

  @Override
  public boolean isEssl() {
    return prototype.isEssl();
  }

  @Override
  public boolean isWebGl() {
    return prototype.isWebGl();
  }

  @Override
  public boolean supportedArrayConstructors() {
    return prototype.supportedArrayConstructors();
  }

  @Override
  public boolean restrictedForLoops() {
    return prototype.restrictedForLoops();
  }

  @Override
  public boolean supportedAbsInt() {
    return prototype.supportedAbsInt();
  }

  @Override
  public boolean supportedBitwiseOperations() {
    return prototype.supportedBitwiseOperations();
  }

  @Override
  public boolean supportedClampInt() {
    return prototype.supportedClampInt();
  }

  @Override
  public boolean supportedClampUint() {
    return prototype.supportedClampUint();
  }

  @Override
  public boolean supportedComputeShaders() {
    return prototype.supportedComputeShaders();
  }

  @Override
  public boolean supportedDerivativeFunctions() {
    return prototype.supportedDerivativeFunctions();
  }

  @Override
  public boolean supportedDeterminant() {
    return prototype.supportedDeterminant();
  }

  @Override
  public boolean supportedDoStmt() {
    return prototype.supportedDoStmt();
  }

  @Override
  public boolean supportedExplicitDerivativeFunctions() {
    return prototype.supportedExplicitDerivativeFunctions();
  }

  @Override
  public boolean supportedFloatBitsToInt() {
    return prototype.supportedFloatBitsToInt();
  }

  @Override
  public boolean supportedFloatBitsToUint() {
    return prototype.supportedFloatBitsToUint();
  }

  @Override
  public boolean supportedFma() {
    return prototype.supportedFma();
  }

  @Override
  public boolean supportedGlFragColor() {
    return prototype.supportedGlFragColor();
  }

  @Override
  public boolean supportedIntBitsToFloat() {
    return prototype.supportedIntBitsToFloat();
  }

  @Override
  public boolean supportedIntegerFunctions() {
    return prototype.supportedIntegerFunctions();
  }

  @Override
  public boolean supportedInterpolationFunctions() {
    return prototype.supportedInterpolationFunctions();
  }

  @Override
  public boolean supportedInverse() {
    return prototype.supportedInverse();
  }

  @Override
  public boolean supportedIsinf() {
    return prototype.supportedIsinf();
  }

  @Override
  public boolean supportedIsnan() {
    return prototype.supportedIsnan();
  }

  @Override
  public boolean supportedMatrixCompMultNonSquare() {
    return prototype.supportedMatrixCompMultNonSquare();
  }

  @Override
  public boolean supportedMaxInt() {
    return prototype.supportedMaxInt();
  }

  @Override
  public boolean supportedMaxUint() {
    return prototype.supportedMaxUint();
  }

  @Override
  public boolean supportedMinInt() {
    return prototype.supportedMinInt();
  }

  @Override
  public boolean supportedMinUint() {
    return prototype.supportedMinUint();
  }

  @Override
  public boolean supportedMixFloatBool() {
    return prototype.supportedMixFloatBool();
  }

  @Override
  public boolean supportedMixNonfloatBool() {
    return prototype.supportedMixNonfloatBool();
  }

  @Override
  public boolean supportedNonSquareMatrices() {
    return prototype.supportedNonSquareMatrices();
  }

  @Override
  public boolean supportedOuterProduct() {
    return prototype.supportedOuterProduct();
  }

  @Override
  public boolean supportedPackHalf2x16() {
    return prototype.supportedPackHalf2x16();
  }

  @Override
  public boolean supportedPackSnorm2x16() {
    return prototype.supportedPackSnorm2x16();
  }

  @Override
  public boolean supportedPackSnorm4x8() {
    return prototype.supportedPackSnorm4x8();
  }

  @Override
  public boolean supportedPackUnorm2x16() {
    return prototype.supportedPackUnorm2x16();
  }

  @Override
  public boolean supportedPackUnorm4x8() {
    return prototype.supportedPackUnorm4x8();
  }

  @Override
  public boolean supportedSignInt() {
    return prototype.supportedSignInt();
  }

  @Override
  public boolean supportedRound() {
    return prototype.supportedRound();
  }

  @Override
  public boolean supportedRoundEven() {
    return prototype.supportedRoundEven();
  }

  @Override
  public boolean supportedSwitchStmt() {
    return prototype.supportedSwitchStmt();
  }

  @Override
  public boolean supportedTranspose() {
    return prototype.supportedTranspose();
  }

  @Override
  public boolean supportedTrunc() {
    return prototype.supportedTrunc();
  }

  @Override
  public boolean supportedUintBitsToFloat() {
    return prototype.supportedUintBitsToFloat();
  }

  @Override
  public boolean supportedUnpackHalf2x16() {
    return prototype.supportedUnpackHalf2x16();
  }

  @Override
  public boolean supportedUnpackSnorm2x16() {
    return prototype.supportedUnpackSnorm2x16();
  }

  @Override
  public boolean supportedUnpackSnorm4x8() {
    return prototype.supportedUnpackSnorm4x8();
  }

  @Override
  public boolean supportedUnpackUnorm2x16() {
    return prototype.supportedUnpackUnorm2x16();
  }

  @Override
  public boolean supportedUnpackUnorm4x8() {
    return prototype.supportedUnpackUnorm4x8();
  }

  @Override
  public boolean supportedUnsigned() {
    return prototype.supportedUnsigned();
  }

  @Override
  public boolean supportedAngleAndTrigonometricFunctions() {
    return prototype.supportedAngleAndTrigonometricFunctions();
  }

  @Override
  public boolean supportedHyperbolicAngleAndTrigonometricFunctions() {
    return prototype.supportedHyperbolicAngleAndTrigonometricFunctions();
  }

  @Override
  public boolean supportedModf() {
    return prototype.supportedModf();
  }

  @Override
  public boolean supportedFrexp() {
    return prototype.supportedFrexp();
  }

  @Override
  public boolean supportedLdexp() {
    return prototype.supportedLdexp();
  }

  @Override
  public boolean supportedAtomicMemoryFunctions() {
    return prototype.supportedAtomicMemoryFunctions();
  }

  @Override
  public boolean supportedTexture() {
    return prototype.supportedTexture();
  }

  @Override
  public boolean supportedShaderInvocationControlFunctions() {
    return prototype.supportedShaderInvocationControlFunctions();
  }

  @Override
  public boolean supportedShaderMemoryControlFunctions() {
    return prototype.supportedShaderMemoryControlFunctions();
  }

  @Override
  public boolean supportedPushConstants() {
    return prototype.supportedPushConstants();
  }

  @Override
  public boolean supportedScalarSwizzle() {
    return prototype.supportedScalarSwizzle();
  }
}
