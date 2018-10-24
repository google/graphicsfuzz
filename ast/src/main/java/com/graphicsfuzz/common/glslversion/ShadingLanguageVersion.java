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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface ShadingLanguageVersion {

  ShadingLanguageVersion ESSL_100 = Essl100.INSTANCE;
  ShadingLanguageVersion ESSL_300 = Essl300.INSTANCE;
  ShadingLanguageVersion ESSL_310 = Essl310.INSTANCE;

  ShadingLanguageVersion GLSL_110 = Glsl110.INSTANCE;
  ShadingLanguageVersion GLSL_120 = Glsl120.INSTANCE;
  ShadingLanguageVersion GLSL_130 = Glsl130.INSTANCE;
  ShadingLanguageVersion GLSL_140 = Glsl140.INSTANCE;
  ShadingLanguageVersion GLSL_150 = Glsl150.INSTANCE;
  ShadingLanguageVersion GLSL_330 = Glsl330.INSTANCE;
  ShadingLanguageVersion GLSL_400 = Glsl400.INSTANCE;
  ShadingLanguageVersion GLSL_410 = Glsl410.INSTANCE;
  ShadingLanguageVersion GLSL_420 = Glsl420.INSTANCE;
  ShadingLanguageVersion GLSL_430 = Glsl430.INSTANCE;
  ShadingLanguageVersion GLSL_440 = Glsl440.INSTANCE;
  ShadingLanguageVersion GLSL_450 = Glsl450.INSTANCE;
  ShadingLanguageVersion GLSL_460 = Glsl460.INSTANCE;

  ShadingLanguageVersion WEBGL_SL = WebGlSl.INSTANCE;
  ShadingLanguageVersion WEBGL2_SL = WebGl2Sl.INSTANCE;

  static ShadingLanguageVersion getGlslVersionFromFirstTwoLines(String[] lines)
        throws IOException {
    String[] components = lines[0].trim().split(" ");
    if (!lines[0].startsWith("#version") || components.length < 2) {
      final String message = "File must specify a version on the first line, using #version";
      System.err
            .println(message);
      throw new RuntimeException(
            message);
    }
    String version = components[1];
    for (int i = 2; i < components.length; i++) {
      version += " " + components[i];
    }
    if (isWebGlHint(lines[1])) {
      return webGlFromVersionString(version);
    }
    return fromVersionString(version);
  }

  static boolean isWebGlHint(String line) {
    return line.startsWith("//WebGL");
  }

  static List<ShadingLanguageVersion> allGlslVersions() {
    return Arrays.asList(
        GLSL_110,
        GLSL_120,
        GLSL_130,
        GLSL_140,
        GLSL_150,
        GLSL_330,
        GLSL_400,
        GLSL_410,
        GLSL_420,
        GLSL_430,
        GLSL_440,
        GLSL_450,
        GLSL_460);
  }

  static List<ShadingLanguageVersion> allEsslVersions() {
    return Arrays.asList(
        ESSL_100,
        ESSL_300,
        ESSL_310);
  }

  static List<ShadingLanguageVersion> allWebGlSlVersions() {
    return Arrays.asList(
        WEBGL_SL,
        WEBGL2_SL);
  }

  static List<ShadingLanguageVersion> allShadingLanguageVersions() {
    final List<ShadingLanguageVersion> versions = new ArrayList<>();
    versions.addAll(allGlslVersions());
    versions.addAll(allEsslVersions());
    versions.addAll(allWebGlSlVersions());
    return versions;
  }

  static ShadingLanguageVersion fromVersionString(String versionString) {
    final List<ShadingLanguageVersion> versions = new ArrayList<>();
    versions.addAll(allGlslVersions());
    versions.addAll(allEsslVersions());
    for (ShadingLanguageVersion shadingLanguageVersion : versions) {
      if (shadingLanguageVersion.getVersionString().equals(versionString)) {
        return shadingLanguageVersion;
      }
    }
    throw new RuntimeException("Unknown version string " + versionString);
  }

  static ShadingLanguageVersion webGlFromVersionString(String versionString) {
    for (ShadingLanguageVersion shadingLanguageVersion : allWebGlSlVersions()) {
      if (shadingLanguageVersion.getVersionString().equals(versionString)) {
        return shadingLanguageVersion;
      }
    }
    throw new RuntimeException("Unknown WebGL shading language version string " + versionString);
  }

  String getVersionString();

  boolean globalVariableInitializersMustBeConst();

  boolean initializersOfConstMustBeConst();

  boolean isWebGl();

  boolean restrictedArrayIndexing();

  boolean restrictedForLoops();

  boolean supportedAbsInt();

  boolean supportedBitwiseOperations();

  boolean supportedClampInt();

  boolean supportedClampUint();

  boolean supportedDeterminant();

  boolean supportedDoStmt();

  boolean supportedFloatBitsToInt();

  boolean supportedFloatBitsToUint();

  boolean supportedFma();

  boolean supportedGlFragColor();

  boolean supportedIntBitsToFloat();

  boolean supportedInverse();

  boolean supportedIsinf();

  boolean supportedIsnan();

  boolean supportedMatrixCompMultNonSquare();

  boolean supportedMaxInt();

  boolean supportedMaxUint();

  boolean supportedMinInt();

  boolean supportedMinUint();

  boolean supportedMixFloatBool();

  boolean supportedMixNonfloatBool();

  boolean supportedNonSquareMatrices();

  boolean supportedOuterProduct();

  boolean supportedPackHalf2x16();

  boolean supportedPackSnorm2x16();

  boolean supportedPackSnorm4x8();

  boolean supportedPackUnorm2x16();

  boolean supportedPackUnorm4x8();

  boolean supportedRound();

  boolean supportedRoundEven();

  boolean supportedSignInt();

  boolean supportedSwitchStmt();

  boolean supportedTranspose();

  boolean supportedTrunc();

  boolean supportedUintBitsToFloat();

  boolean supportedUnpackHalf2x16();

  boolean supportedUnpackSnorm2x16();

  boolean supportedUnpackSnorm4x8();

  boolean supportedUnpackUnorm2x16();

  boolean supportedUnpackUnorm4x8();

  boolean supportedUnsigned();

}