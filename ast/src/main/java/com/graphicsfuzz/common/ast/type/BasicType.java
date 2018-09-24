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

package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BasicType extends BuiltinType {

  private BasicType() {
    // BasicType is in essence an enumeration.
    // No need to override .equals() and .hashCode()
  }

  public static final BasicType FLOAT = new BasicType();
  public static final BasicType INT = new BasicType();
  public static final BasicType UINT = new BasicType();
  public static final BasicType BOOL = new BasicType();
  public static final BasicType VEC2 = new BasicType();
  public static final BasicType VEC3 = new BasicType();
  public static final BasicType VEC4 = new BasicType();
  public static final BasicType BVEC2 = new BasicType();
  public static final BasicType BVEC3 = new BasicType();
  public static final BasicType BVEC4 = new BasicType();
  public static final BasicType IVEC2 = new BasicType();
  public static final BasicType IVEC3 = new BasicType();
  public static final BasicType IVEC4 = new BasicType();
  public static final BasicType UVEC2 = new BasicType();
  public static final BasicType UVEC3 = new BasicType();
  public static final BasicType UVEC4 = new BasicType();
  public static final BasicType MAT2X2 = new BasicType();
  public static final BasicType MAT2X3 = new BasicType();
  public static final BasicType MAT2X4 = new BasicType();
  public static final BasicType MAT3X2 = new BasicType();
  public static final BasicType MAT3X3 = new BasicType();
  public static final BasicType MAT3X4 = new BasicType();
  public static final BasicType MAT4X2 = new BasicType();
  public static final BasicType MAT4X3 = new BasicType();
  public static final BasicType MAT4X4 = new BasicType();

  @Override
  public String toString() {
    if (this == FLOAT) {
      return "float";
    }
    if (this == INT) {
      return "int";
    }
    if (this == UINT) {
      return "uint";
    }
    if (this == BOOL) {
      return "bool";
    }
    if (this == VEC2) {
      return "vec2";
    }
    if (this == VEC3) {
      return "vec3";
    }
    if (this == VEC4) {
      return "vec4";
    }
    if (this == BVEC2) {
      return "bvec2";
    }
    if (this == BVEC3) {
      return "bvec3";
    }
    if (this == BVEC4) {
      return "bvec4";
    }
    if (this == IVEC2) {
      return "ivec2";
    }
    if (this == IVEC3) {
      return "ivec3";
    }
    if (this == IVEC4) {
      return "ivec4";
    }
    if (this == UVEC2) {
      return "uvec2";
    }
    if (this == UVEC3) {
      return "uvec3";
    }
    if (this == UVEC4) {
      return "uvec4";
    }
    if (this == MAT2X2) {
      return "mat2";
    }
    if (this == MAT2X3) {
      return "mat2x3";
    }
    if (this == MAT2X4) {
      return "mat2x4";
    }
    if (this == MAT3X2) {
      return "mat3x2";
    }
    if (this == MAT3X3) {
      return "mat3";
    }
    if (this == MAT3X4) {
      return "mat3x4";
    }
    if (this == MAT4X2) {
      return "mat4x2";
    }
    if (this == MAT4X3) {
      return "mat4x3";
    }
    if (this == MAT4X4) {
      return "mat4";
    }
    throw new RuntimeException("Invalid type");
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitBasicType(this);
  }

  @Override
  public boolean hasCanonicalConstant() {
    return true;
  }

  @Override
  public Expr getCanonicalConstant() {
    if (this == FLOAT) {
      return new FloatConstantExpr("1.0");
    }
    if (this == INT) {
      return new IntConstantExpr("1");
    }
    if (this == UINT) {
      return new UIntConstantExpr("1u");
    }
    if (this == BOOL) {
      return BoolConstantExpr.TRUE;
    }
    return new TypeConstructorExpr(toString().toString(), getElementType().getCanonicalConstant());
  }

  public BasicType getElementType() {
    if (Arrays.asList(INT, IVEC2, IVEC3, IVEC4).contains(this)) {
      return INT;
    }
    if (Arrays.asList(UINT, UVEC2, UVEC3, UVEC4).contains(this)) {
      return UINT;
    }
    if (Arrays.asList(BOOL, BVEC2, BVEC3, BVEC4).contains(this)) {
      return BOOL;
    }
    return FLOAT; // Covers float, float vectors, and matrices
  }

  public int getNumElements() {
    if (Arrays.asList(FLOAT, INT, UINT, BOOL).contains(this)) {
      return 1;
    }
    if (Arrays.asList(VEC2, IVEC2, UVEC2, BVEC2).contains(this)) {
      return 2;
    }
    if (Arrays.asList(VEC3, IVEC3, UVEC3, BVEC3).contains(this)) {
      return 3;
    }
    if (Arrays.asList(VEC4, IVEC4, UVEC4, BVEC4).contains(this)) {
      return 4;
    }
    if (this == MAT2X2) {
      return 4;
    }
    if (this == MAT2X3) {
      return 6;
    }
    if (this == MAT2X4) {
      return 8;
    }
    if (this == MAT3X2) {
      return 6;
    }
    if (this == MAT3X3) {
      return 9;
    }
    if (this == MAT3X4) {
      return 12;
    }
    if (this == MAT4X2) {
      return 8;
    }
    if (this == MAT4X3) {
      return 12;
    }
    if (this == MAT4X4) {
      return 16;
    }
    throw new RuntimeException("Error getting number of elements for " + this);
  }

  public boolean isBoolean() {
    return getElementType() == BOOL;
  }

  public static List<BasicType> allScalarTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        FLOAT,
        INT,
        UINT,
        BOOL));
  }

  public static List<BasicType> allBasicTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        FLOAT,
        VEC2,
        VEC3,
        VEC4,
        UINT,
        UVEC2,
        UVEC3,
        UVEC4,
        INT,
        IVEC2,
        IVEC3,
        IVEC4,
        BOOL,
        BVEC2,
        BVEC3,
        BVEC4,
        MAT2X2,
        MAT2X3,
        MAT2X4,
        MAT3X2,
        MAT3X3,
        MAT3X4,
        MAT4X2,
        MAT4X3,
        MAT4X4));
  }

  public static List<BasicType> allVectorTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        VEC2,
        VEC3,
        VEC4,
        UVEC2,
        UVEC3,
        UVEC4,
        IVEC2,
        IVEC3,
        IVEC4,
        BVEC2,
        BVEC3,
        BVEC4));
  }

  public static List<BasicType> allMatrixTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        MAT2X2,
        MAT2X3,
        MAT2X4,
        MAT3X2,
        MAT3X3,
        MAT3X4,
        MAT4X2,
        MAT4X3,
        MAT4X4));
  }

  public static List<BasicType> allSquareMatrixTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        MAT2X2,
        MAT3X3,
        MAT4X4));
  }

  public static List<BasicType> allNonSquareMatrixTypes() {
    List<BasicType> result = allMatrixTypes();
    result.removeAll(allSquareMatrixTypes());
    return result;
  }

  public static List<BasicType> allUnsignedTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
        UINT,
        UVEC2,
        UVEC3,
        UVEC4));
  }

  public static List<BasicType> allGenTypes() {
    return new ArrayList<>(Arrays.asList(FLOAT, VEC2, VEC3, VEC4));
  }

  public static List<BasicType> allNumericTypes() {
    final List<BasicType> result = allBasicTypes();
    result.removeAll(allBoolTypes());
    return result;
  }

  public static List<BasicType> allNumericTypesExceptNonSquareMatrices() {
    final List<BasicType> result = allNumericTypes();
    result.removeAll(allNonSquareMatrixTypes());
    return result;
  }

  public static List<BasicType> allNonMatrixNumericTypes() {
    final List<BasicType> result = allNumericTypes();
    result.removeAll(allMatrixTypes());
    return result;
  }

  private static List<BasicType> allBoolTypes() {
    return new ArrayList<BasicType>(Arrays.asList(
          BOOL,
          BVEC2,
          BVEC3,
          BVEC4));
  }

  public static int numColumns(BasicType basicType) {
    assert allMatrixTypes().contains(basicType);
    if (Arrays.asList(MAT2X2, MAT2X3, MAT2X4).contains(basicType)) {
      return 2;
    }
    if (Arrays.asList(MAT3X2, MAT3X3, MAT3X4).contains(basicType)) {
      return 3;
    }
    if (Arrays.asList(MAT4X2, MAT4X3, MAT4X4).contains(basicType)) {
      return 4;
    }
    throw new RuntimeException("Camnnot get number of columns for " + basicType);
  }

  public static BasicType makeVectorType(BasicType elementType, int numElements) {
    if (!allScalarTypes().contains(elementType)) {
      throw new RuntimeException("Cannot make vector type from element type " + elementType);
    }
    if (numElements < 0 || numElements > 4) {
      throw new RuntimeException("Cannot make vector type with " + numElements + " elements");
    }
    if (elementType == FLOAT) {
      switch (numElements) {
        case 1:
          return FLOAT;
        case 2:
          return VEC2;
        case 3:
          return VEC3;
        default:
          assert numElements == 4;
          return VEC4;
      }
    }
    if (elementType == INT) {
      switch (numElements) {
        case 1:
          return INT;
        case 2:
          return IVEC2;
        case 3:
          return IVEC3;
        default:
          assert numElements == 4;
          return IVEC4;
      }
    }
    if (elementType == UINT) {
      switch (numElements) {
        case 1:
          return UINT;
        case 2:
          return UVEC2;
        case 3:
          return UVEC3;
        default:
          assert numElements == 4;
          return UVEC4;
      }
    }
    if (elementType == BOOL) {
      switch (numElements) {
        case 1:
          return BOOL;
        case 2:
          return BVEC2;
        case 3:
          return BVEC3;
        default:
          assert numElements == 4;
          return BVEC4;
      }
    }
    assert false;
    return null;
  }

  public boolean isScalar() {
    return allScalarTypes().contains(this);
  }

  public boolean isVector() {
    return allVectorTypes().contains(this);
  }

  public boolean isMatrix() {
    return allMatrixTypes().contains(this);
  }

  public BasicType getColumnType() {
    assert allMatrixTypes().contains(this);
    if (Arrays.asList(BasicType.MAT2X2, BasicType.MAT3X2, BasicType.MAT4X2).contains(this)) {
      return VEC2;
    }
    if (Arrays.asList(BasicType.MAT2X3, BasicType.MAT3X3, BasicType.MAT4X3).contains(this)) {
      return VEC3;
    }
    assert Arrays.asList(BasicType.MAT2X4, BasicType.MAT3X4, BasicType.MAT4X4).contains(this);
    return VEC4;
  }

  public int getNumColumns() {
    assert allMatrixTypes().contains(this);
    if (Arrays.asList(BasicType.MAT2X2, BasicType.MAT2X3, BasicType.MAT2X4).contains(this)) {
      return 2;
    }
    if (Arrays.asList(BasicType.MAT3X2, BasicType.MAT3X3, BasicType.MAT3X4).contains(this)) {
      return 3;
    }
    assert Arrays.asList(BasicType.MAT4X2, BasicType.MAT4X3, BasicType.MAT4X4).contains(this);
    return 4;
  }
}
