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

final class WebGlSl extends CompositeShadingLanguageVersion {

  static final ShadingLanguageVersion INSTANCE = new WebGlSl(Essl100.INSTANCE);

  private WebGlSl(ShadingLanguageVersion prototype) {
    super(prototype);
    // Singleton
  }

  @Override
  public String getVersionString() {
    return "100";
  }

  @Override
  public boolean isWebGl() {
    return true;
  }

  @Override
  public boolean supportedDoStmt() {
    // do ... while loops are not supported in WebGL 1.0
    return false;
  }

}
