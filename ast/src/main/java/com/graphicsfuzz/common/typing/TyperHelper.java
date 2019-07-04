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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * This helper class factors out some context-independent parts of typechecking,
 * on which other classes will depend.
 */
public final class TyperHelper {

  // Maps a (shading language version, shader kind) pair to a mapping from builtin names to builtin
  // function prototypes.
  private static ConcurrentMap<ShadingLanguageVersion,
      ConcurrentMap<ShaderKind, Map<String, List<FunctionPrototype>>>> builtins =
      new ConcurrentHashMap<>();

  private TyperHelper() {
    // Utility class
  }

  public static Type resolveTypeOfCommonBinary(Type lhsType, Type rhsType) {
    // If they match, the result must be the same
    if (lhsType == rhsType) {
      return lhsType;
    }
    // If one side is scalar and the other side is basic, the result has to be that of the other
    // side
    if (lhsType == BasicType.FLOAT || lhsType == BasicType.INT || lhsType == BasicType.UINT) {
      if (rhsType instanceof BasicType) {
        return rhsType;
      }
      return null;
    }
    if (rhsType == BasicType.FLOAT || rhsType == BasicType.INT || rhsType == BasicType.UINT) {
      if (lhsType instanceof BasicType) {
        return lhsType;
      }
      return null;
    }
    // Now we are in a position where if we know that one type is vector
    // or matrix, the other side must be also
    for (BasicType t : Arrays.asList(
        BasicType.VEC2,
        BasicType.VEC3,
        BasicType.VEC4,
        BasicType.IVEC2,
        BasicType.IVEC3,
        BasicType.IVEC4,
        BasicType.UVEC2,
        BasicType.UVEC3,
        BasicType.UVEC4,
        BasicType.MAT2X2,
        BasicType.MAT2X3,
        BasicType.MAT2X4,
        BasicType.MAT3X2,
        BasicType.MAT3X3,
        BasicType.MAT3X4,
        BasicType.MAT4X2,
        BasicType.MAT4X3,
        BasicType.MAT4X4)) {
      if (lhsType == t || rhsType == t) {
        return t;
      }
    }
    return null;
  }

  public static Type resolveTypeOfMul(Type lhsType, Type rhsType) {
    // If they match, the result must be the same
    if (lhsType == rhsType) {
      return lhsType;
    }

    // If one side is scalar and the other side is basic, the result has to be that of the other
    // side
    if (lhsType == BasicType.FLOAT || lhsType == BasicType.INT || lhsType == BasicType.UINT) {
      if (rhsType instanceof BasicType) {
        return rhsType;
      }
      return null;
    }
    if (rhsType == BasicType.FLOAT || rhsType == BasicType.INT || rhsType == BasicType.UINT) {
      if (lhsType instanceof BasicType) {
        return lhsType;
      }
      return null;
    }

    // For integer and unsigned integer vectors, we are now in a position where if we see that
    // one side is a vector we know the result type must be a vector
    if (lhsType == BasicType.UVEC2 || rhsType == BasicType.UVEC2) {
      return BasicType.UVEC2;
    }
    if (lhsType == BasicType.UVEC3 || rhsType == BasicType.UVEC3) {
      return BasicType.UVEC3;
    }
    if (lhsType == BasicType.UVEC4 || rhsType == BasicType.UVEC4) {
      return BasicType.UVEC4;
    }
    if (lhsType == BasicType.IVEC2 || rhsType == BasicType.IVEC2) {
      return BasicType.IVEC2;
    }
    if (lhsType == BasicType.IVEC3 || rhsType == BasicType.IVEC3) {
      return BasicType.IVEC3;
    }
    if (lhsType == BasicType.IVEC4 || rhsType == BasicType.IVEC4) {
      return BasicType.IVEC4;
    }

    // Now for floating point vectors and matrices we need to be careful,
    // so we just consider all the cases
    if (lhsType == BasicType.VEC2 && rhsType == BasicType.VEC2) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.VEC3 && rhsType == BasicType.VEC3) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.VEC4 && rhsType == BasicType.VEC4) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.VEC2 && rhsType == BasicType.MAT2X2) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.VEC2 && rhsType == BasicType.MAT3X2) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.VEC2 && rhsType == BasicType.MAT4X2) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT2X2 && rhsType == BasicType.VEC2) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.MAT2X2 && rhsType == BasicType.MAT2X2) {
      return BasicType.MAT2X2;
    }
    if (lhsType == BasicType.MAT2X2 && rhsType == BasicType.MAT3X2) {
      return BasicType.MAT3X2;
    }
    if (lhsType == BasicType.MAT2X2 && rhsType == BasicType.MAT4X2) {
      return BasicType.MAT4X2;
    }
    if (lhsType == BasicType.MAT2X3 && rhsType == BasicType.VEC2) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.MAT2X3 && rhsType == BasicType.MAT2X2) {
      return BasicType.MAT2X3;
    }
    if (lhsType == BasicType.MAT2X3 && rhsType == BasicType.MAT3X2) {
      return BasicType.MAT3X3;
    }
    if (lhsType == BasicType.MAT2X3 && rhsType == BasicType.MAT4X2) {
      return BasicType.MAT4X3;
    }
    if (lhsType == BasicType.MAT2X4 && rhsType == BasicType.VEC2) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT2X4 && rhsType == BasicType.MAT2X2) {
      return BasicType.MAT2X4;
    }
    if (lhsType == BasicType.MAT2X4 && rhsType == BasicType.MAT3X2) {
      return BasicType.MAT3X4;
    }
    if (lhsType == BasicType.MAT2X4 && rhsType == BasicType.MAT4X2) {
      return BasicType.MAT4X4;
    }
    if (lhsType == BasicType.VEC3 && rhsType == BasicType.MAT2X3) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.VEC3 && rhsType == BasicType.MAT3X3) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.VEC3 && rhsType == BasicType.MAT4X3) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT3X2 && rhsType == BasicType.VEC3) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.MAT3X2 && rhsType == BasicType.MAT2X3) {
      return BasicType.MAT2X2;
    }
    if (lhsType == BasicType.MAT3X2 && rhsType == BasicType.MAT3X3) {
      return BasicType.MAT3X2;
    }
    if (lhsType == BasicType.MAT3X2 && rhsType == BasicType.MAT4X3) {
      return BasicType.MAT4X2;
    }
    if (lhsType == BasicType.MAT3X3 && rhsType == BasicType.VEC3) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.MAT3X3 && rhsType == BasicType.MAT2X3) {
      return BasicType.MAT2X3;
    }
    if (lhsType == BasicType.MAT3X3 && rhsType == BasicType.MAT3X3) {
      return BasicType.MAT3X3;
    }
    if (lhsType == BasicType.MAT3X3 && rhsType == BasicType.MAT4X3) {
      return BasicType.MAT4X3;
    }
    if (lhsType == BasicType.MAT3X4 && rhsType == BasicType.VEC3) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT3X4 && rhsType == BasicType.MAT2X3) {
      return BasicType.MAT2X4;
    }
    if (lhsType == BasicType.MAT3X4 && rhsType == BasicType.MAT3X3) {
      return BasicType.MAT3X4;
    }
    if (lhsType == BasicType.MAT3X4 && rhsType == BasicType.MAT4X3) {
      return BasicType.MAT4X4;
    }
    if (lhsType == BasicType.VEC4 && rhsType == BasicType.MAT2X4) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.VEC4 && rhsType == BasicType.MAT3X4) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.VEC4 && rhsType == BasicType.MAT4X4) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT4X2 && rhsType == BasicType.VEC4) {
      return BasicType.VEC2;
    }
    if (lhsType == BasicType.MAT4X2 && rhsType == BasicType.MAT2X4) {
      return BasicType.MAT2X2;
    }
    if (lhsType == BasicType.MAT4X2 && rhsType == BasicType.MAT3X4) {
      return BasicType.MAT3X2;
    }
    if (lhsType == BasicType.MAT4X2 && rhsType == BasicType.MAT4X4) {
      return BasicType.MAT4X2;
    }
    if (lhsType == BasicType.MAT4X3 && rhsType == BasicType.VEC4) {
      return BasicType.VEC3;
    }
    if (lhsType == BasicType.MAT4X3 && rhsType == BasicType.MAT2X4) {
      return BasicType.MAT2X3;
    }
    if (lhsType == BasicType.MAT4X3 && rhsType == BasicType.MAT3X4) {
      return BasicType.MAT3X3;
    }
    if (lhsType == BasicType.MAT4X3 && rhsType == BasicType.MAT4X4) {
      return BasicType.MAT4X3;
    }
    if (lhsType == BasicType.MAT4X4 && rhsType == BasicType.VEC4) {
      return BasicType.VEC4;
    }
    if (lhsType == BasicType.MAT4X4 && rhsType == BasicType.MAT2X4) {
      return BasicType.MAT2X4;
    }
    if (lhsType == BasicType.MAT4X4 && rhsType == BasicType.MAT3X4) {
      return BasicType.MAT3X4;
    }
    if (lhsType == BasicType.MAT4X4 && rhsType == BasicType.MAT4X4) {
      return BasicType.MAT4X4;
    }
    return null;
  }

  /**
   * Yield the builtins available for the given shading language version and kind of shader.
   *
   * @param shadingLanguageVersion version of GLSL for which relevant builtins should be returned.
   * @param shaderKind kind of shader (e.g. fragment or compute) for which relevant builtins
   *                   should be returned.
   * @return a mapping from name of builtin to sequence of function prototypes.
   */
  public static Map<String, List<FunctionPrototype>> getBuiltins(ShadingLanguageVersion
      shadingLanguageVersion, ShaderKind shaderKind) {

    assert shadingLanguageVersion != null;
    assert shaderKind != null;

    if (!builtins.containsKey(shadingLanguageVersion)) {
      builtins.putIfAbsent(shadingLanguageVersion, new ConcurrentHashMap<>());
    }
    if (!builtins.get(shadingLanguageVersion).containsKey(shaderKind)) {
      builtins.get(shadingLanguageVersion).putIfAbsent(shaderKind,
          getBuiltinsForGlslVersion(shadingLanguageVersion, shaderKind));
    }
    return Collections.unmodifiableMap(builtins.get(shadingLanguageVersion).get(shaderKind));
  }

  private static Map<String, List<FunctionPrototype>> getBuiltinsForGlslVersion(
      ShadingLanguageVersion shadingLanguageVersion, ShaderKind shaderKind) {
    Map<String, List<FunctionPrototype>> builtinsForVersion = new HashMap<>();

    // 8.1: Angle and Trigonometric Functions

    getBuiltinsForGlslVersionAngleAndTrigonometric(shadingLanguageVersion,
        builtinsForVersion);

    // 8.2: Exponential Functions

    getBuiltinsForGlslVersionExponential(builtinsForVersion);

    // 8.3: Common Functions

    {
      final String name = "abs";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
      if (shadingLanguageVersion.supportedAbsInt()) {
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }
    }

    {
      final String name = "sign";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
      if (shadingLanguageVersion.supportedSignInt()) {
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }
    }

    {
      final String name = "floor";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    if (shadingLanguageVersion.supportedTrunc()) {
      final String name = "trunc";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    if (shadingLanguageVersion.supportedRound()) {
      final String name = "round";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    if (shadingLanguageVersion.supportedRoundEven()) {
      final String name = "roundEven";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "ceil";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "fract";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "mod";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, BasicType.FLOAT);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
    }

    // TODO: genType modf(genType, out genType)

    {
      final String name = "min";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, BasicType.FLOAT);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
      if (shadingLanguageVersion.supportedMinInt()) {
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.INT);
          if (t != BasicType.INT) {
            addBuiltin(builtinsForVersion, name, t, t, t);
          }
        }
      }
      if (shadingLanguageVersion.supportedMinUint()) {
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.UINT);
          if (t != BasicType.UINT) {
            addBuiltin(builtinsForVersion, name, t, t, t);
          }
        }
      }
    }

    {
      final String name = "max";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, BasicType.FLOAT);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
      if (shadingLanguageVersion.supportedMaxInt()) {
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.INT);
          if (t != BasicType.INT) {
            addBuiltin(builtinsForVersion, name, t, t, t);
          }
        }
      }
      if (shadingLanguageVersion.supportedMaxUint()) {
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.UINT);
          if (t != BasicType.UINT) {
            addBuiltin(builtinsForVersion, name, t, t, t);
          }
        }
      }
    }

    {
      final String name = "clamp";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, BasicType.FLOAT, BasicType.FLOAT);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t, t);
        }
      }
      if (shadingLanguageVersion.supportedClampInt()) {
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.INT, BasicType.INT);
          if (t != BasicType.INT) {
            addBuiltin(builtinsForVersion, name, t, t, t, t);
          }
        }
      }
      if (shadingLanguageVersion.supportedClampUint()) {
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.UINT, BasicType.UINT);
          if (t != BasicType.UINT) {
            addBuiltin(builtinsForVersion, name, t, t, t, t);
          }
        }
      }
    }

    {
      final String name = "mix";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t, BasicType.FLOAT);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t, t);
        }
      }
      if (shadingLanguageVersion.supportedMixFloatBool()) {
        addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
            BasicType.BOOL);
        addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.VEC2, BasicType.VEC2,
            BasicType.BVEC2);
        addBuiltin(builtinsForVersion, name, BasicType.VEC3, BasicType.VEC3, BasicType.VEC3,
            BasicType.BVEC3);
        addBuiltin(builtinsForVersion, name, BasicType.VEC4, BasicType.VEC4, BasicType.VEC4,
            BasicType.BVEC4);
      }

      if (shadingLanguageVersion.supportedMixNonfloatBool()) {
        addBuiltin(builtinsForVersion, name, BasicType.INT, BasicType.INT, BasicType.INT,
            BasicType.BOOL);
        addBuiltin(builtinsForVersion, name, BasicType.IVEC2, BasicType.IVEC2, BasicType.IVEC2,
            BasicType.BVEC2);
        addBuiltin(builtinsForVersion, name, BasicType.IVEC3, BasicType.IVEC3, BasicType.IVEC3,
            BasicType.BVEC3);
        addBuiltin(builtinsForVersion, name, BasicType.IVEC4, BasicType.IVEC4, BasicType.IVEC4,
            BasicType.BVEC4);

        addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.UINT, BasicType.UINT,
            BasicType.BOOL);
        addBuiltin(builtinsForVersion, name, BasicType.UVEC2, BasicType.UVEC2, BasicType.UVEC2,
            BasicType.BVEC2);
        addBuiltin(builtinsForVersion, name, BasicType.UVEC3, BasicType.UVEC3, BasicType.UVEC3,
            BasicType.BVEC3);
        addBuiltin(builtinsForVersion, name, BasicType.UVEC4, BasicType.UVEC4, BasicType.UVEC4,
            BasicType.BVEC4);

        addBuiltin(builtinsForVersion, name, BasicType.BOOL, BasicType.BOOL, BasicType.BOOL,
            BasicType.BOOL);
        addBuiltin(builtinsForVersion, name, BasicType.BVEC2, BasicType.BVEC2, BasicType.BVEC2,
            BasicType.BVEC2);
        addBuiltin(builtinsForVersion, name, BasicType.BVEC3, BasicType.BVEC3, BasicType.BVEC3,
            BasicType.BVEC3);
        addBuiltin(builtinsForVersion, name, BasicType.BVEC4, BasicType.BVEC4, BasicType.BVEC4,
            BasicType.BVEC4);
      }
    }

    {
      final String name = "step";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, BasicType.FLOAT, t);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
    }

    {
      final String name = "smoothstep";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, BasicType.FLOAT, BasicType.FLOAT, t);
        if (t != BasicType.FLOAT) {
          addBuiltin(builtinsForVersion, name, t, t, t, t);
        }
      }
    }

    if (shadingLanguageVersion.supportedIsnan()) {
      final String name = "isnan";
      addBuiltin(builtinsForVersion, name, BasicType.BOOL, BasicType.FLOAT);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC2, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC3, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC4, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedIsinf()) {
      final String name = "isinf";
      addBuiltin(builtinsForVersion, name, BasicType.BOOL, BasicType.FLOAT);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC2, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC3, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.BVEC4, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedFloatBitsToInt()) {
      final String name = "floatBitsToInt";
      addBuiltin(builtinsForVersion, name, BasicType.INT, BasicType.FLOAT);
      addBuiltin(builtinsForVersion, name, BasicType.IVEC2, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.IVEC3, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.IVEC4, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedFloatBitsToUint()) {
      final String name = "floatBitsToUint";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.FLOAT);
      addBuiltin(builtinsForVersion, name, BasicType.UVEC2, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.UVEC3, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.UVEC4, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedIntBitsToFloat()) {
      final String name = "intBitsToFloat";
      addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.INT);
      addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.IVEC2);
      addBuiltin(builtinsForVersion, name, BasicType.VEC3, BasicType.IVEC3);
      addBuiltin(builtinsForVersion, name, BasicType.VEC4, BasicType.IVEC4);
    }

    if (shadingLanguageVersion.supportedUintBitsToFloat()) {
      final String name = "uintBitsToFloat";
      addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.UINT);
      addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.UVEC2);
      addBuiltin(builtinsForVersion, name, BasicType.VEC3, BasicType.UVEC3);
      addBuiltin(builtinsForVersion, name, BasicType.VEC4, BasicType.UVEC4);
    }

    if (shadingLanguageVersion.supportedFma()) {
      final String name = "fma";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t, t);
      }
    }

    {
      @SuppressWarnings("unused")
      final String name = "frexp";
      // TODO: genType frexp(genType, out genIType)
    }

    {
      @SuppressWarnings("unused")
      final String name = "ldexp";
      // TODO: genType frexp(genType, in genIType)
    }

    // 8.4: Floating-Point Pack and Unpack Functions

    getBuiltinsForGlslVersionFloatingPointPackAndUnpack(builtinsForVersion, shadingLanguageVersion);

    // 8.5: Geometric Functions

    getBuiltinsForGlslVersionGeometric(builtinsForVersion);

    // 8.6: Matrix Functions

    getBuiltinsForGlslVersionMatrix(builtinsForVersion, shadingLanguageVersion);

    // 8.7: Vector Relational Functions

    getBuiltinsForGlslVersionVectorRelational(builtinsForVersion, shadingLanguageVersion);

    // 8.8: Integer Functions

    getBuiltinsForGlslVersionInteger(builtinsForVersion, shadingLanguageVersion);

    // 8.13: Fragment Processing Functions (only available in fragment shaders)

    if (shaderKind == ShaderKind.FRAGMENT) {
      getBuiltinsForGlslVersionFragmentProcessing(builtinsForVersion, shadingLanguageVersion);
    }

    // 8.14: Noise Functions - deprecated, so we do not consider them

    return builtinsForVersion;
  }

  /**
   * Helper function to register built-in function prototypes for Angle and Trigonometric
   * Functions, as specified in section 8.1 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   * @param shadingLanguageVersion the version of GLSL in use
   */
  private static void getBuiltinsForGlslVersionAngleAndTrigonometric(
      ShadingLanguageVersion shadingLanguageVersion,
      Map<String, List<FunctionPrototype>> builtinsForVersion) {
    if (shadingLanguageVersion.supportedAngleAndTrigonometricFunctions()) {
      {
        final String name = "radians";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "degrees";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "sin";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "cos";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "tan";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "asin";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "acos";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "atan";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
    }

    if (shadingLanguageVersion.supportedHyperbolicAngleAndTrigonometricFunctions()) {
      {
        final String name = "sinh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "cosh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "tanh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "asinh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "acosh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "atanh";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }
    }
  }

  /**
   * Helper function to register built-in function prototypes for Exponential Functions, as
   * specified in section 8.2 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   */
  private static void getBuiltinsForGlslVersionExponential(
      Map<String, List<FunctionPrototype>> builtinsForVersion) {
    {
      final String name = "pow";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t);
      }
    }

    {
      final String name = "exp";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "log";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "exp2";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "log2";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "sqrt";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "inversesqrt";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }
  }

  private static void getBuiltinsForGlslVersionVectorRelational(
      Map<String, List<FunctionPrototype>> builtinsForVersion,
      ShadingLanguageVersion shadingLanguageVersion) {
    // We need these for every function, so instead of constantly calling the functions,
    // we'll just cache them to reduce cruft.
    final List<Type> genVectors = genType().stream().filter(
        item -> !BasicType.allScalarTypes().contains(item)).collect(Collectors.toList());
    final List<Type> igenVectors = igenType().stream().filter(
        item -> !BasicType.allScalarTypes().contains(item)).collect(Collectors.toList());
    final List<Type> ugenVectors = ugenType().stream().filter(
        item -> !BasicType.allScalarTypes().contains(item)).collect(Collectors.toList());
    final List<Type> bgenVectors = bgenType().stream().filter(
        item -> !BasicType.allScalarTypes().contains(item)).collect(Collectors.toList());
    final boolean supportsUnsigned = shadingLanguageVersion.supportedUnsigned();

    {
      final String name = "lessThan";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
      }
    }

    {
      final String name = "lessThanEqual";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
      }
    }

    {
      final String name = "greaterThan";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
      }
    }

    {
      final String name = "greaterThanEqual";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
      }
    }

    {
      final String name = "equal";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), bgenVectors.get(i),
            bgenVectors.get(i));
      }
    }

    {
      final String name = "notEqual";
      for (int i = 0; i < bgenVectors.size(); i++) {
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), genVectors.get(i),
            genVectors.get(i));
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), igenVectors.get(i),
            igenVectors.get(i));
        if (supportsUnsigned) {
          addBuiltin(builtinsForVersion, name, bgenVectors.get(i), ugenVectors.get(i),
              ugenVectors.get(i));
        }
        addBuiltin(builtinsForVersion, name, bgenVectors.get(i), bgenVectors.get(i),
            bgenVectors.get(i));
      }
    }

    {
      final String name = "any";
      for (Type t : bgenVectors) {
        addBuiltin(builtinsForVersion, name, BasicType.BOOL, t);
      }
    }

    {
      final String name = "all";
      for (Type t : bgenVectors) {
        addBuiltin(builtinsForVersion, name, BasicType.BOOL, t);
      }
    }

    {
      final String name = "not";
      for (Type t : bgenVectors) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }
  }


  /**
   * Helper function to register built-in function prototypes for Integer Functions,
   * as specified in section 8.8 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   * @param shadingLanguageVersion the version of GLSL in use
   */
  private static void getBuiltinsForGlslVersionInteger(
      Map<String, List<FunctionPrototype>> builtinsForVersion,
      ShadingLanguageVersion shadingLanguageVersion) {
    if (shadingLanguageVersion.supportedIntegerFunctions()) {
      {
        final String name = "uaddCarry";
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, t, new QualifiedType(t,
              Arrays.asList(TypeQualifier.OUT_PARAM)));
        }
      }

      {
        final String name = "usubBorrow";
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, t, new QualifiedType(t,
              Arrays.asList(TypeQualifier.OUT_PARAM)));
        }
      }

      {
        final String name = "umulExtended";
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, VoidType.VOID, t, t,
              new QualifiedType(t, Arrays.asList(TypeQualifier.OUT_PARAM)),
              new QualifiedType(t, Arrays.asList(TypeQualifier.OUT_PARAM)));
        }
      }

      {
        final String name = "imulExtended";
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, VoidType.VOID, t, t,
              new QualifiedType(t, Arrays.asList(TypeQualifier.OUT_PARAM)),
              new QualifiedType(t, Arrays.asList(TypeQualifier.OUT_PARAM)));
        }
      }

      {
        final String name = "bitfieldExtract";
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.INT, BasicType.INT);
        }
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, BasicType.INT, BasicType.INT);
        }
      }

      {
        final String name = "bitfieldInsert";
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t, t, BasicType.INT, BasicType.INT);
        }
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t, t, BasicType.INT, BasicType.INT);
        }
      }

      {
        final String name = "bitfieldReverse";
        for (Type t : igenType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
        for (Type t : ugenType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      // We need to use both igen and ugen types of the same size for these builtins, so we need a
      // counting loop instead of an iterator to access both lists at the same time.
      {
        final String name = "bitCount";
        for (int i = 0; i < igenType().size(); i++) {
          addBuiltin(builtinsForVersion, name, igenType().get(i), igenType().get(i));
          addBuiltin(builtinsForVersion, name, igenType().get(i), ugenType().get(i));
        }
      }

      {
        final String name = "findLSB";
        for (int i = 0; i < igenType().size(); i++) {
          addBuiltin(builtinsForVersion, name, igenType().get(i), igenType().get(i));
          addBuiltin(builtinsForVersion, name, igenType().get(i), ugenType().get(i));
        }
      }

      {
        final String name = "findMSB";
        for (int i = 0; i < igenType().size(); i++) {
          addBuiltin(builtinsForVersion, name, igenType().get(i), igenType().get(i));
          addBuiltin(builtinsForVersion, name, igenType().get(i), ugenType().get(i));
        }
      }
    }
  }

  /**
   * Helper function to register built-in function prototypes for Fragment Processing Functions,
   * as specified in section 8.14 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   * @param shadingLanguageVersion the version of GLSL in use
   */
  private static void getBuiltinsForGlslVersionFragmentProcessing(
      Map<String, List<FunctionPrototype>> builtinsForVersion,
      ShadingLanguageVersion shadingLanguageVersion) {
    // 8.14.1 Derivative Functions
    if (shadingLanguageVersion.supportedDerivativeFunctions()) {
      {
        final String name = "dFdx";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "dFdy";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "fwidth";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }
    }

    if (shadingLanguageVersion.supportedExplicitDerivativeFunctions()) {
      {
        final String name = "dFdxFine";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "dFdxCoarse";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "dFdyFine";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "dFdyCoarse";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "fwidthFine";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }

      {
        final String name = "fwidthCoarse";
        for (Type t : genType()) {
          addBuiltin(builtinsForVersion, name, t, t);
        }
      }
    }

    // 8.14.2 Interpolation Functions
    // TODO(550): Support functions that take non-uniform shader input variables as parameters.
  }

  /**
   * Helper function to register built-in function prototypes for Matrix Functions,
   * as specified in section 8.6 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   * @param shadingLanguageVersion the version of GLSL in use
   */
  private static void getBuiltinsForGlslVersionMatrix(
      Map<String, List<FunctionPrototype>> builtinsForVersion,
      ShadingLanguageVersion shadingLanguageVersion) {
    {
      final String name = "matrixCompMult";
      for (Type t : BasicType.allMatrixTypes()) {
        if (BasicType.allSquareMatrixTypes().contains(t)
            || shadingLanguageVersion.supportedMatrixCompMultNonSquare()) {
          addBuiltin(builtinsForVersion, name, t, t, t);
        }
      }
    }

    if (shadingLanguageVersion.supportedOuterProduct()) {
      final String name = "outerProduct";
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X2, BasicType.VEC2, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X3, BasicType.VEC3, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X4, BasicType.VEC4, BasicType.VEC4);
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X3, BasicType.VEC3, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X2, BasicType.VEC2, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X4, BasicType.VEC4, BasicType.VEC2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X2, BasicType.VEC2, BasicType.VEC4);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X4, BasicType.VEC4, BasicType.VEC3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X3, BasicType.VEC3, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedTranspose()) {
      final String name = "transpose";
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X2, BasicType.MAT2X2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X2, BasicType.MAT2X3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X2, BasicType.MAT2X4);
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X3, BasicType.MAT3X2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X3, BasicType.MAT3X3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X3, BasicType.MAT3X4);
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X4, BasicType.MAT4X2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X4, BasicType.MAT4X3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X4, BasicType.MAT4X4);
    }

    if (shadingLanguageVersion.supportedDeterminant()) {
      final String name = "determinant";
      addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.MAT2X2);
      addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.MAT3X3);
      addBuiltin(builtinsForVersion, name, BasicType.FLOAT, BasicType.MAT4X4);
    }

    if (shadingLanguageVersion.supportedInverse()) {
      final String name = "inverse";
      addBuiltin(builtinsForVersion, name, BasicType.MAT2X2, BasicType.MAT2X2);
      addBuiltin(builtinsForVersion, name, BasicType.MAT3X3, BasicType.MAT3X3);
      addBuiltin(builtinsForVersion, name, BasicType.MAT4X4, BasicType.MAT4X4);
    }
  }

  /**
   * Helper function to register built-in function prototypes for Geometric Functions,
   * as specified in section 8.5 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   */
  private static void getBuiltinsForGlslVersionGeometric(
      Map<String, List<FunctionPrototype>> builtinsForVersion) {
    {
      final String name = "length";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, BasicType.FLOAT, t);
      }
    }

    {
      final String name = "distance";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, BasicType.FLOAT, t, t);
      }
    }

    {
      final String name = "dot";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, BasicType.FLOAT, t, t);
      }
    }

    {
      final String name = "cross";
      addBuiltin(builtinsForVersion, name, BasicType.VEC3, BasicType.VEC3, BasicType.VEC3);
    }

    {
      final String name = "normalize";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t);
      }
    }

    {
      final String name = "faceforward";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t, t);
      }
    }

    {
      final String name = "reflect";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t);
      }
    }

    {
      final String name = "refract";
      for (Type t : genType()) {
        addBuiltin(builtinsForVersion, name, t, t, t, BasicType.FLOAT);
      }
    }
  }

  /**
   * Helper function to register built-in function prototypes for Floating-Point Pack and
   * Unpack Functions, as specified in section 8.4 of the GLSL 4.6 and ESSL 3.2 specifications.
   *
   * @param builtinsForVersion the list of builtins to add prototypes to
   * @param shadingLanguageVersion the version of GLSL in use
   */
  private static void getBuiltinsForGlslVersionFloatingPointPackAndUnpack(
      Map<String, List<FunctionPrototype>> builtinsForVersion,
      ShadingLanguageVersion shadingLanguageVersion) {

    if (shadingLanguageVersion.supportedPackUnorm2x16()) {
      final String name = "packUnorm2x16";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.VEC2);
    }

    if (shadingLanguageVersion.supportedPackSnorm2x16()) {
      final String name = "packSnorm2x16";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.VEC2);
    }

    if (shadingLanguageVersion.supportedPackUnorm4x8()) {
      final String name = "packUnorm4x8";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedPackSnorm4x8()) {
      final String name = "packSnorm4x8";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.VEC4);
    }

    if (shadingLanguageVersion.supportedUnpackUnorm2x16()) {
      final String name = "unpackUnorm2x16";
      addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.UINT);
    }

    if (shadingLanguageVersion.supportedUnpackSnorm2x16()) {
      final String name = "unpackSnorm2x16";
      addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.UINT);
    }

    if (shadingLanguageVersion.supportedUnpackUnorm4x8()) {
      final String name = "unpackUnorm4x8";
      addBuiltin(builtinsForVersion, name, BasicType.VEC4, BasicType.UINT);
    }

    if (shadingLanguageVersion.supportedUnpackSnorm4x8()) {
      final String name = "unpackSnorm4x8";
      addBuiltin(builtinsForVersion, name, BasicType.VEC4, BasicType.UINT);
    }

    if (shadingLanguageVersion.supportedPackHalf2x16()) {
      final String name = "packHalf2x16";
      addBuiltin(builtinsForVersion, name, BasicType.UINT, BasicType.VEC2);
    }

    if (shadingLanguageVersion.supportedUnpackHalf2x16()) {
      final String name = "unpackHalf2x16";
      addBuiltin(builtinsForVersion, name, BasicType.VEC2, BasicType.UINT);
    }
  }

  private static void addBuiltin(Map<String, List<FunctionPrototype>> builtinsForVersion,
      String name, Type resultType, Type... args) {
    if (!builtinsForVersion.containsKey(name)) {
      builtinsForVersion.put(name, new ArrayList<>());
    }
    builtinsForVersion.get(name).add(new FunctionPrototype(name, resultType, args));
  }

  private static List<Type> genType() {
    return Arrays.asList(BasicType.FLOAT, BasicType.VEC2, BasicType.VEC3, BasicType.VEC4);
  }

  private static List<Type> igenType() {
    return Arrays.asList(BasicType.INT, BasicType.IVEC2, BasicType.IVEC3, BasicType.IVEC4);
  }

  private static List<Type> ugenType() {
    return Arrays.asList(BasicType.UINT, BasicType.UVEC2, BasicType.UVEC3, BasicType.UVEC4);
  }

  @SuppressWarnings("unused")
  private static List<Type> bgenType() {
    return Arrays.asList(BasicType.BOOL, BasicType.BVEC2, BasicType.BVEC3, BasicType.BVEC4);
  }

}
