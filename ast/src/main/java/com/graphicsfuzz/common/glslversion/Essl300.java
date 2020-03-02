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

final class Essl300 extends CompositeShadingLanguageVersion {

  static final ShadingLanguageVersion INSTANCE = new Essl300(Essl100.INSTANCE);

  private Essl300(ShadingLanguageVersion prototype) {
    super(prototype);
    // Singleton
  }

  @Override
  public String getVersionString() {
    return "300 es";
  }

  @Override
  public boolean supportedArrayConstructors() {
    return true;
  }

  @Override
  public boolean restrictedForLoops() {
    return false;
  }

  @Override
  public boolean supportedAbsInt() {
    return true;
  }

  @Override
  public boolean supportedBitwiseOperations() {
    return true;
  }

  @Override
  public boolean supportedClampInt() {
    return true;
  }

  @Override
  public boolean supportedClampUint() {
    return true;
  }

  @Override
  public boolean supportedDerivativeFunctions() {
    return true;
  }

  @Override
  public boolean supportedDeterminant() {
    return true;
  }

  @Override
  public boolean supportedDoStmt() {
    return true;
  }

  @Override
  public boolean supportedFloatBitsToInt() {
    return true;
  }

  @Override
  public boolean supportedFloatBitsToUint() {
    return true;
  }

  @Override
  public boolean supportedGlFragColor() {
    return false;
  }

  @Override
  public boolean supportedIntBitsToFloat() {
    return true;
  }

  @Override
  public boolean supportedInverse() {
    return true;
  }

  @Override
  public boolean supportedIsinf() {
    return true;
  }

  @Override
  public boolean supportedIsnan() {
    return true;
  }

  @Override
  public boolean supportedMatrixCompMultNonSquare() {
    return true;
  }

  @Override
  public boolean supportedMaxInt() {
    return true;
  }

  @Override
  public boolean supportedMaxUint() {
    return true;
  }

  @Override
  public boolean supportedMinInt() {
    return true;
  }

  @Override
  public boolean supportedMinUint() {
    return true;
  }

  @Override
  public boolean supportedMixFloatBool() {
    return true;
  }

  @Override
  public boolean supportedNonSquareMatrices() {
    return true;
  }

  @Override
  public boolean supportedOuterProduct() {
    return true;
  }

  @Override
  public boolean supportedPackHalf2x16() {
    return true;
  }

  @Override
  public boolean supportedPackSnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedPackUnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedRound() {
    return true;
  }

  @Override
  public boolean supportedRoundEven() {
    return true;
  }

  @Override
  public boolean supportedSwitchStmt() {
    return true;
  }

  @Override
  public boolean supportedSignInt() {
    return true;
  }

  @Override
  public boolean supportedTranspose() {
    return true;
  }

  @Override
  public boolean supportedTrunc() {
    return true;
  }

  @Override
  public boolean supportedUintBitsToFloat() {
    return true;
  }

  @Override
  public boolean supportedUnpackHalf2x16() {
    return true;
  }

  @Override
  public boolean supportedUnpackSnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedUnpackUnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedUnsigned() {
    return true;
  }

  @Override
  public boolean supportedHyperbolicAngleAndTrigonometricFunctions() {
    return true;
  }

  @Override
  public boolean supportedModf() {
    return true;
  }

  @Override
  public boolean supportedTexture() {
    return true;
  }

}
