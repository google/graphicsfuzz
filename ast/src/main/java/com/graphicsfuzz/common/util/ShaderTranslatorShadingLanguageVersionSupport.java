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
