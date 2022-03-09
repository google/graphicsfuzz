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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface ShadingLanguageVersion {

  ShadingLanguageVersion ESSL_100 = Essl100.INSTANCE;
  ShadingLanguageVersion ESSL_300 = Essl300.INSTANCE;
  ShadingLanguageVersion ESSL_310 = Essl310.INSTANCE;
  ShadingLanguageVersion ESSL_320 = Essl320.INSTANCE;

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

  static ShadingLanguageVersion getGlslVersionFromFirstTwoLines(String[] lines) {
    String[] components = lines[0].trim().split(" ");
    if (!lines[0].startsWith("#version") || components.length < 2) {
      // The default shading language version is 100 es.
      return ESSL_100;
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
        ESSL_310,
        ESSL_320);
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

  static ShadingLanguageVersion fromVersionString(String versionString, boolean webGlHint) {
    final ShadingLanguageVersion regularVersion = fromVersionString(versionString);
    if (webGlHint) {
      if (regularVersion == ESSL_100) {
        return WEBGL_SL;
      }
      if (regularVersion == ESSL_300) {
        return WEBGL2_SL;
      }
    }
    return regularVersion;
  }

  static boolean isWebGlCompatible(String versionString) {
    return allWebGlSlVersions().stream().map(item -> item.getVersionString())
        .anyMatch(item -> item.equals(versionString));
  }

  static ShadingLanguageVersion webGlFromVersionString(String versionString) {
    if (!isWebGlCompatible(versionString)) {
      throw new RuntimeException("Unknown WebGL shading language version string " + versionString);
    }
    return allWebGlSlVersions()
        .stream()
        .filter(item -> item.getVersionString().equals(versionString))
        .findAny().get();
  }

  String getVersionString();

  boolean globalVariableInitializersMustBeConst();

  boolean initializersOfConstMustBeConst();

  // Returns true if and only if this is an ES shading language.
  boolean isEssl();

  boolean isWebGl();

  /**
   * GLSL versions 1.2+ and ESSL versions 3.0+ support array constructors.
   * @return true if and only if array constructors are supported.
   */
  boolean supportedArrayConstructors();

  boolean restrictedForLoops();

  boolean supportedAbsInt();

  boolean supportedBitwiseOperations();

  boolean supportedClampInt();

  boolean supportedClampUint();

  /**
   * GLSL versions 4.3+ and ESSL versions 3.1+ support compute shaders.
   * @return true if the shading language version allows compute shaders - false otherwise.
   */
  boolean supportedComputeShaders();

  /**
   * Derivative Functions are a subset of fragment processing functions that compute
   * the rate of change between pixels in a given fragment.
   * GLSL versions 1.1+ and ESSL versions 3.0+ support these functions.
   *
   * @return true if explicit derivative functions are supported - false otherwise.
   */
  boolean supportedDerivativeFunctions();

  /**
   * Determinant Function calculates the determinant of a given square matrix.
   * GLSL versions 1.5+ and ESSL versions 3.0+ support this function.
   *
   * @return true if Determinant Function is supported - false otherwise.
   */
  boolean supportedDeterminant();

  boolean supportedDoStmt();

  /**
   * In recent GLSL specifications, new derivative functions were added that allow a user to
   * specify how much precision the user wants in the calculation, instead of leaving the choice
   * to the compiler.
   * GLSL versions 4.5+ support these explicit derivative functions.
   *
   * @return true if explicit derivative functions are supported - false otherwise.
   */
  boolean supportedExplicitDerivativeFunctions();

  boolean supportedFloatBitsToInt();

  boolean supportedFloatBitsToUint();

  boolean supportedFma();

  boolean supportedGlFragColor();

  boolean supportedIntBitsToFloat();

  /**
   * Integer Functions are a set of built-in functions that allow manipulation of integers and
   * their corresponding vectors in ways difficult or impossible with normal GLSL syntax - for
   * example, summing two unsigned integers where the result causes an overflow.
   * GLSL versions 4.0+ and ESSL versions 3.1+ support these functions.
   *
   * @return true if Integer Functions are supported - false otherwise.
   */
  boolean supportedIntegerFunctions();

  /**
   * Interpolation Functions are a subset of fragment processing functions that
   * compute an interpolated value of a fragment shader input variable at a specific location.
   * GLSL versions 4.0+ and ESSL versions 3.2+ support these functions.
   *
   * @return true if Interpolation Functions are supported - false otherwise.
   */
  boolean supportedInterpolationFunctions();

  /**
   * Inverse Function returns the matrix that is the inverse of the given square matrix.
   * GLSL versions 1.5+ and ESSL versions 3.0+ support this function.
   *
   * @return true if Inverse Function is supported - false otherwise.
   */
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

  /**
   * OuterProduct Function does a linear algebraic matrix multiplication of two given vectors.
   * GLSL versions 1.2+ and ESSL versions 3.0+ support this function.
   *
   * @return true if OuterProduct Function is supported - false otherwise.
   */
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

  /**
   * Transpose Function returns the transposed matrix of the given matrix.
   * GLSL versions 1.2+ and ESSL versions 3.0+ support this function.
   *
   * @return true if Transpose Function is supported - false otherwise.
   */
  boolean supportedTranspose();

  boolean supportedTrunc();

  boolean supportedUintBitsToFloat();

  boolean supportedUnpackHalf2x16();

  boolean supportedUnpackSnorm2x16();

  boolean supportedUnpackSnorm4x8();

  boolean supportedUnpackUnorm2x16();

  boolean supportedUnpackUnorm4x8();

  boolean supportedUnsigned();

  boolean supportedModf();

  boolean supportedFrexp();

  boolean supportedLdexp();

  /**
   * Angle and Trigonometric Functions are a set of built-in functions related to the calculation
   * of an angle. For example, sin(angle) - computes the sine value of the angle provided.
   * GLSL versions 1.1+ and ESSL versions 1.0+ support these functions.
   *
   * @return true if Angle and Trigonometric Functions are supported - false otherwise.
   */
  boolean supportedAngleAndTrigonometricFunctions();

  /**
   * Hyperbolic Angle and Trigonometric Functions are a set of built-in functions that
   * computes the hyperbolic trigonometric functions. For example, sinh() - calculate the
   * hyperbolic sine function of the given value.
   * GLSL versions 1.3+ and ESSL versions 3.0+ support these functions.
   *
   * @return true if Hyperbolic Angle and Trigonometric Functions are supported - false otherwise.
   */
  boolean supportedHyperbolicAngleAndTrigonometricFunctions();

  /**
   * Atomic Memory Functions are a set of built-in functions that perform read-modify-write atomic
   * operations.  For example, atomicAdd() - atomically increment an integer and return its old
   * value.
   * GLSL versions 4.3+ and ESSL versions 3.1+ support these functions.
   *
   * @return true if Atomic Memory Functions are supported - false otherwise.
   */
  boolean supportedAtomicMemoryFunctions();

  /**
   * Indicates whether the shading language supports basic 'texture' functions that were introduced
   * in GLSL 1.30 and ESSL 3.0.
   *
   * @return true if basic texture functions are supported.
   */
  boolean supportedTexture();

  /**
   * GLSL versions 4.0+ and ESSL versions 3.1+ support a barrier function to synchronize a
   * workgroup in a compute shader.
   * @return true if and only if shader invocation control functions are supported.
   */
  boolean supportedShaderInvocationControlFunctions();

  /**
   * GLSL versions 4.0+ and ESSL versions 3.1+ support various memory barrier functions, such as
   * memoryBarrier(), in compute shaders.
   * @return true if and only if shader memory control functions are supported.
   */
  boolean supportedShaderMemoryControlFunctions();

  /**
   * GLSL versions 4.6+ and ESSL versions 3.2+ support push_constants for Vulkan.
   * @return true if and only if push constants are supported.
   */
  boolean supportedPushConstants();

  /**
   * GLSL versions 4.2+ support scalar swizzles, such as v.x.x
   * @return true if and only if scalar swizzles are supported.
   */
  boolean supportedScalarSwizzle();
}
