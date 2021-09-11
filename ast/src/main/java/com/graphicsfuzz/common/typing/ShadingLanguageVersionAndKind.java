/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.Objects;

public class ShadingLanguageVersionAndKind {
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final boolean isWgslCompatible;
  private final ShaderKind shaderKind;

  public ShadingLanguageVersionAndKind(ShadingLanguageVersion shadingLanguageVersion,
                                  boolean isWgslCompatible, ShaderKind shaderKind) {
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.isWgslCompatible = isWgslCompatible;
    this.shaderKind = shaderKind;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (that == null || getClass() != that.getClass()) {
      return false;
    }
    ShadingLanguageVersionAndKind thatShadingLanguageVersionAndKind =
        (ShadingLanguageVersionAndKind) that;
    return isWgslCompatible == thatShadingLanguageVersionAndKind.isWgslCompatible
        && Objects.equals(shadingLanguageVersion,
        thatShadingLanguageVersionAndKind.shadingLanguageVersion)
        && shaderKind == thatShadingLanguageVersionAndKind.shaderKind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(shadingLanguageVersion, isWgslCompatible, shaderKind);
  }
}
