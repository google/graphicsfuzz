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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.util.Arrays;

public final class ShaderTranslatorShadingLanguageVersionSupport {

  private ShaderTranslatorShadingLanguageVersionSupport() {
    // Utility class
  }

  public static boolean isVersionSupported(ShadingLanguageVersion shadingLanguageVersion) {
    // Shader translator does have a -s=e31 option, but it is still marked as "in development".
    return Arrays.asList(ShadingLanguageVersion.WEBGL_SL,
        ShadingLanguageVersion.WEBGL2_SL,
        ShadingLanguageVersion.ESSL_100,
        ShadingLanguageVersion.ESSL_300).contains(shadingLanguageVersion);
  }

  public static String getShaderTranslatorArgument(ShadingLanguageVersion shadingLanguageVersion) {
    if (!isVersionSupported(shadingLanguageVersion)) {
      throw new IllegalArgumentException("Shader translator does not support the given shading"
          + " language version.");
    }
    if (shadingLanguageVersion == ShadingLanguageVersion.WEBGL_SL) {
      return "-s=w";
    } else if (shadingLanguageVersion == ShadingLanguageVersion.WEBGL2_SL) {
      return "-s=w2";
    } else if (shadingLanguageVersion == ShadingLanguageVersion.ESSL_100) {
      return "-s=e2";
    }
    assert shadingLanguageVersion == ShadingLanguageVersion.ESSL_300;
    return "-s=e3";
  }

}
