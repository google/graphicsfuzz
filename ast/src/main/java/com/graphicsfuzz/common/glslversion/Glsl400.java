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

final class Glsl400 extends CompositeShadingLanguageVersion {

  static final ShadingLanguageVersion INSTANCE = new Glsl400(Glsl330.INSTANCE);

  private Glsl400(ShadingLanguageVersion prototype) {
    super(prototype);
    // Singleton
  }

  @Override
  public String getVersionString() {
    return "400";
  }

  @Override
  public boolean supportedFma() {
    return true;
  }

  @Override
  public boolean supportedIntegerFunctions() {
    return true;
  }

  @Override
  public boolean supportedInterpolationFunctions() {
    return true;
  }

  @Override
  public boolean supportedPackSnorm4x8() {
    return true;
  }

  @Override
  public boolean supportedPackUnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedPackUnorm4x8() {
    return true;
  }

  @Override
  public boolean supportedUnpackSnorm4x8() {
    return true;
  }

  @Override
  public boolean supportedUnpackUnorm2x16() {
    return true;
  }

  @Override
  public boolean supportedUnpackUnorm4x8() {
    return true;
  }

  @Override
  public boolean supportedFrexp() {
    return true;
  }

  @Override
  public boolean supportedLdexp() {
    return true;
  }

  @Override
  public boolean supportedShaderInvocationControlFunctions() {
    return true;
  }

  @Override
  public boolean supportedShaderMemoryControlFunctions() {
    return true;
  }
}
