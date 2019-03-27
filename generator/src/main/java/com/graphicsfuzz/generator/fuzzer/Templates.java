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

package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.SupportedTypes;
import com.graphicsfuzz.common.typing.TyperHelper;
import com.graphicsfuzz.generator.fuzzer.templates.BinaryExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.ConstantExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.FunctionCallExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.IExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.ParenExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.SwizzleExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.TernaryExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.TypeConstructorExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.UnaryExprTemplate;
import com.graphicsfuzz.generator.fuzzer.templates.VectorMatrixIndexExprTemplate;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class Templates {

  private static ConcurrentMap<ShadingLanguageVersion, List<IExprTemplate>> templates
        = new ConcurrentHashMap<>();

  private Templates() {
    // Utility class
  }

  public static List<IExprTemplate> get(ShadingLanguageVersion shadingLanguageVersion) {
    if (!templates.containsKey(shadingLanguageVersion)) {
      templates.putIfAbsent(shadingLanguageVersion, makeTemplates(shadingLanguageVersion));
    }
    return Collections.unmodifiableList(templates.get(shadingLanguageVersion));
  }

  public static List<IExprTemplate> makeTemplates(ShadingLanguageVersion shadingLanguageVersion) {

    // TODO: assignment operators, array, vector and matrix lookups

    List<IExprTemplate> templates = new ArrayList<>();

    // Builtins
    {
      Map<String, List<FunctionPrototype>> builtins = TyperHelper.getBuiltins(
          shadingLanguageVersion);
      List<String> keys = builtins.keySet().stream().collect(Collectors.toList());
      keys.sort(String::compareTo);
      for (String key : keys) {
        for (FunctionPrototype prototype : builtins.get(key)) {
          addTemplate(templates, new FunctionCallExprTemplate(prototype));
        }
      }
    }

    // Constants
    for (BasicType type : supportedBasicTypes(shadingLanguageVersion)) {
      addTemplate(templates, new ConstantExprTemplate(type));
    }

    // Parentheses
    for (BasicType type : supportedBasicTypes(shadingLanguageVersion)) {
      addTemplate(templates, new ParenExprTemplate(type, true));
      addTemplate(templates, new ParenExprTemplate(type, false));
    }

    // Casting
    if (shadingLanguageVersion.supportedUnsigned()) {
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.FLOAT, BasicType.UINT));
    }
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.FLOAT, BasicType.INT));
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.FLOAT, BasicType.BOOL));
    if (shadingLanguageVersion.supportedUnsigned()) {
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.UINT, BasicType.FLOAT));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.UINT, BasicType.INT));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.UINT, BasicType.BOOL));
    }
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.INT, BasicType.FLOAT));
    if (shadingLanguageVersion.supportedUnsigned()) {
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.INT, BasicType.UINT));
    }
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.INT, BasicType.BOOL));
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.BOOL, BasicType.FLOAT));
    if (shadingLanguageVersion.supportedUnsigned()) {
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.BOOL, BasicType.UINT));
    }
    addTemplate(templates, new TypeConstructorExprTemplate(BasicType.BOOL, BasicType.INT));

    // Vectors
    {
      BasicType[][] types = new BasicType[][]{
          {BasicType.FLOAT, BasicType.VEC2, BasicType.VEC3, BasicType.VEC4},
          {BasicType.UINT, BasicType.UVEC2, BasicType.UVEC3, BasicType.UVEC4},
          {BasicType.INT, BasicType.IVEC2, BasicType.IVEC3, BasicType.IVEC4},
          {BasicType.BOOL, BasicType.BVEC2, BasicType.BVEC3, BasicType.BVEC4}
      };

      for (int i = 0; i < types.length; i++) {
        final BasicType width1 = types[i][0];
        final BasicType width2 = types[i][1];
        final BasicType width3 = types[i][2];
        final BasicType width4 = types[i][3];
        addTemplate(templates, new TypeConstructorExprTemplate(width2, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width2, width1, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width2, width3));
        addTemplate(templates, new TypeConstructorExprTemplate(width2, width4));
        addTemplate(templates, new TypeConstructorExprTemplate(width3, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width3, width1, width1, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width3, width1, width2));
        addTemplate(templates, new TypeConstructorExprTemplate(width3, width2, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width3, width4));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width1));
        addTemplate(templates,
              new TypeConstructorExprTemplate(width4, width1, width1, width1, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width1, width1, width2));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width1, width2, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width2, width1, width1));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width2, width2));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width1, width3));
        addTemplate(templates, new TypeConstructorExprTemplate(width4, width3, width1));
      }
    }

    // Matrices
    {
      // 2x2
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.FLOAT));
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT3X3));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT4X4));
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.VEC2, BasicType.VEC2));
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X2, BasicType.MAT4X3));
      }

      // 2x3
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT4X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT2X3, BasicType.VEC3, BasicType.VEC3));
      }

      // 2x4
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT,
                    BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT4X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT2X4, BasicType.VEC4, BasicType.VEC4));
      }

      // 3x2
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT4X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT3X2, BasicType.VEC2, BasicType.VEC2,
                    BasicType.VEC2));
      }

      // 3x3
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.FLOAT));
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT2X2));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT4X4));
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.VEC3, BasicType.VEC3,
                  BasicType.VEC3));
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X3, BasicType.MAT4X3));
      }

      // 3x4
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT4X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT3X4, BasicType.VEC4, BasicType.VEC4,
                    BasicType.VEC4));
      }

      // 4x2
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT,
                    BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT4X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT4X2, BasicType.VEC2, BasicType.VEC2,
                    BasicType.VEC2, BasicType.VEC2));
      }

      // 4x3
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.FLOAT));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT,
                    BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                    BasicType.FLOAT));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT2X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT3X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.MAT4X4));
        addTemplate(templates,
              new TypeConstructorExprTemplate(BasicType.MAT4X3, BasicType.VEC3, BasicType.VEC3,
                    BasicType.VEC3, BasicType.VEC3));
      }

      // 4x4
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.FLOAT));
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT,
                  BasicType.FLOAT,
                  BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT, BasicType.FLOAT));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT2X2));
      addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT3X3));
      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT2X3));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT2X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT3X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT3X4));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT4X2));
        addTemplate(templates, new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.MAT4X3));
      }
      addTemplate(templates,
            new TypeConstructorExprTemplate(BasicType.MAT4X4, BasicType.VEC4, BasicType.VEC4,
                  BasicType.VEC4, BasicType.VEC4));

    }

    /* Text taken from The OpenGL Shading Language 4.50, revision 5, Section 5.9 */

    /*
      The arithmetic binary operators add (+), subtract (-), multiply (*), and divide (/) operate
      on integer and floating-point scalars, vectors, and matrices. If the fundamental types in the
      operands do not match, then the conversions from section 4.1.10 "Implicit Conversions" are
      applied to create matching types.  All arithmetic binary operators result in the same
      fundamental type (signed integer, unsigned integer, single-precision floating point, or
      double-precision floating point) as the operands they operate on, after operand type
      conversion. After conversion, the following cases are valid
      * The two operands are scalars. In this case the operation is applied, resulting in a scalar.
      * One operand is a scalar, and the other is a vector or matrix. In this case, the scalar
        operation is applied independently to each component of the vector or matrix, resulting in
        the same size vector or matrix.
      * The two operands are vectors of the same size. In this case, the operation is done
        component-wise resulting in the same size vector.
      * The operator is add (+), subtract (-), or divide (/), and the operands are matrices with
        the same number of rows and the same number of columns. In this case, the operation is done
        component-wise resulting in the same size matrix.
      * The operator is multiply (*), where both operands are matrices or one operand is a vector
        and the other a matrix. A right vector operand is treated as a column vector and a left
        vector operand as a row vector. In all these cases, it is required that the number of
        columns of the left operand is equal to the number of rows of the right operand. Then, the
        multiply (*) operation does a linear algebraic multiply, yielding an object that has the
        same number of rows as the left operand and the same number of columns as the right operand.
        Section 5.10 "Vector and Matrix Operations" explains in more detail how vectors and matrices
        are operated on.
      All other cases result in a compile-time error.
      Dividing by zero does not cause an exception but does result in an unspecified value. Use the
      built-in functions dot, cross, matrixCompMult, and outerProduct, to get, respectively, vector
      dot product, vector cross product, matrix component-wise multiplication, and the matrix
      product of a column vector times a row vector.
    */

    {
      BinOp[] ops = new BinOp[]{BinOp.ADD, BinOp.SUB, BinOp.DIV, BinOp.MUL};
      for (BinOp op : ops) {
        BasicType[][] vectorTypes = new BasicType[][]{
            {BasicType.FLOAT, BasicType.VEC2, BasicType.VEC3, BasicType.VEC4},
            {BasicType.UINT, BasicType.UVEC2, BasicType.UVEC3, BasicType.UVEC4},
            {BasicType.INT, BasicType.IVEC2, BasicType.IVEC3, BasicType.IVEC4}
        };
        for (BasicType[] vecs : vectorTypes) {
          for (BasicType vec : vecs) {
            addTemplate(templates, new BinaryExprTemplate(vec, vec, vec, op));
            if (vec != vecs[0]) {
              addTemplate(templates, new BinaryExprTemplate(vecs[0], vec, vec, op));
              addTemplate(templates, new BinaryExprTemplate(vec, vecs[0], vec, op));
            }
          }
        }

        if (op == BinOp.MUL) {

          addTemplate(templates,
                new BinaryExprTemplate(BasicType.VEC2, BasicType.MAT2X2, BasicType.VEC2, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT2X2, BasicType.VEC2, BasicType.VEC2, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT2X2, BasicType.MAT2X2, BasicType.MAT2X2, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.VEC3, BasicType.MAT3X3, BasicType.VEC3, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT3X3, BasicType.VEC3, BasicType.VEC3, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT3X3, BasicType.MAT3X3, BasicType.MAT3X3, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.VEC4, BasicType.MAT4X4, BasicType.VEC4, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT4X4, BasicType.VEC4, BasicType.VEC4, op));
          addTemplate(templates,
                new BinaryExprTemplate(BasicType.MAT4X4, BasicType.MAT4X4, BasicType.MAT4X4, op));

          if (shadingLanguageVersion.supportedNonSquareMatrices()) {
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC2, BasicType.MAT3X2, BasicType.VEC3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC2, BasicType.MAT4X2, BasicType.VEC4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X2, BasicType.MAT3X2, BasicType.MAT3X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X2, BasicType.MAT4X2, BasicType.MAT4X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X3, BasicType.VEC2, BasicType.VEC3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X3, BasicType.MAT2X2, BasicType.MAT2X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X3, BasicType.MAT3X2, BasicType.MAT3X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X3, BasicType.MAT4X2, BasicType.MAT4X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X4, BasicType.VEC2, BasicType.VEC4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X4, BasicType.MAT2X2, BasicType.MAT2X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X4, BasicType.MAT3X2, BasicType.MAT3X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT2X4, BasicType.MAT4X2, BasicType.MAT4X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC3, BasicType.MAT2X3, BasicType.VEC2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC3, BasicType.MAT4X3, BasicType.VEC4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X2, BasicType.VEC3, BasicType.VEC2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X2, BasicType.MAT2X3, BasicType.MAT2X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X2, BasicType.MAT3X3, BasicType.MAT3X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X2, BasicType.MAT4X3, BasicType.MAT4X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X3, BasicType.MAT2X3, BasicType.MAT2X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X3, BasicType.MAT4X3, BasicType.MAT4X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X4, BasicType.VEC3, BasicType.VEC4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X4, BasicType.MAT2X3, BasicType.MAT2X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X4, BasicType.MAT3X3, BasicType.MAT3X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT3X4, BasicType.MAT4X3, BasicType.MAT4X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC4, BasicType.MAT2X4, BasicType.VEC2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.VEC4, BasicType.MAT3X4, BasicType.VEC3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X2, BasicType.VEC4, BasicType.VEC2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X2, BasicType.MAT2X4, BasicType.MAT2X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X2, BasicType.MAT3X4, BasicType.MAT3X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X2, BasicType.MAT4X4, BasicType.MAT4X2, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X3, BasicType.VEC4, BasicType.VEC3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X3, BasicType.MAT2X4, BasicType.MAT2X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X3, BasicType.MAT3X4, BasicType.MAT3X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X3, BasicType.MAT4X4, BasicType.MAT4X3, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X4, BasicType.MAT2X4, BasicType.MAT2X4, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.MAT4X4, BasicType.MAT3X4, BasicType.MAT3X4, op));
          }

        } else {
          for (BasicType matrixType : BasicType.allMatrixTypes()) {
            if (!SupportedTypes.supported(matrixType, shadingLanguageVersion)) {
              continue;
            }
            addTemplate(templates, new BinaryExprTemplate(matrixType, matrixType, matrixType, op));
            addTemplate(templates,
                  new BinaryExprTemplate(BasicType.FLOAT, matrixType, matrixType, op));
            addTemplate(templates,
                  new BinaryExprTemplate(matrixType, BasicType.FLOAT, matrixType, op));
          }
        }
      }

    }

    /*
      The operator modulus (%) operates on signed or unsigned integer scalars or integer vectors.
      If the fundamental types in the operands do not match, then the conversions from section
      4.1.10 "Implicit Conversions" are applied to create matching types. The operands cannot be
      vectors of differing size; this is a compile time error. If one operand is a scalar and the
      other vector, then the scalar is applied component-wise to the vector, resulting in the same
      type as the vector. If both are vectors of the same size, the result is computed component-
      wise. The resulting value is undefined for any component computed with a second operand that
      is zero, while results for other components with non-zero second operands remain defined. If
      both operands are non-negative, then the remainder is non-negative.  Results are undefined if
      one or both operands are negative. The operator modulus (%) is not defined for any other data
      types (non-integer types).
    */

    if (shadingLanguageVersion.supportedUnsigned()) {
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UINT, BasicType.UINT, BasicType.UINT, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC2, BasicType.UVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC3, BasicType.UVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC4, BasicType.UVEC4, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC2, BasicType.UINT, BasicType.UVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC3, BasicType.UINT, BasicType.UVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC4, BasicType.UINT, BasicType.UVEC4, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC2, BasicType.UVEC2, BasicType.UVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC3, BasicType.UVEC3, BasicType.UVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.UVEC4, BasicType.UVEC4, BasicType.UVEC4, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.INT, BasicType.INT, BasicType.INT, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.INT, BasicType.IVEC2, BasicType.IVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.INT, BasicType.IVEC3, BasicType.IVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.INT, BasicType.IVEC4, BasicType.IVEC4, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC2, BasicType.INT, BasicType.IVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC3, BasicType.INT, BasicType.IVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC4, BasicType.INT, BasicType.IVEC4, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC2, BasicType.IVEC2, BasicType.IVEC2, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC3, BasicType.IVEC3, BasicType.IVEC3, BinOp.MOD));
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.IVEC4, BasicType.IVEC4, BasicType.IVEC4, BinOp.MOD));
    }

    /*
      The arithmetic unary operators negate (-), post- and pre-increment and decrement (-- and ++)
      operate on integer or floating-point values (including vectors and matrices). All unary
      operators work component-wise on their operands. These result with the same type they operated
      on. For post- and pre-increment and decrement, the expression must be one that could be
      assigned to (an l-value). Pre-increment and pre-decrement add or subtract 1 or 1.0 to the
      contents of the expression they operate on, and the value of the pre-increment or pre-
      decrement expression is the resulting value of that modification. Post-increment and post-
      decrement expressions add or subtract 1 or 1.0 to the contents of the expression they operate
      on, but the resulting expression has the expression's value before the post-increment or post-
      decrement was executed.
    */

    for (BasicType type : new BasicType[]{
        BasicType.FLOAT,
        BasicType.VEC2,
        BasicType.VEC3,
        BasicType.VEC4,
        BasicType.UINT,
        BasicType.UVEC2,
        BasicType.UVEC3,
        BasicType.UVEC4,
        BasicType.INT,
        BasicType.IVEC2,
        BasicType.IVEC3,
        BasicType.IVEC4,
        BasicType.MAT2X2,
        BasicType.MAT2X3,
        BasicType.MAT2X4,
        BasicType.MAT3X2,
        BasicType.MAT3X3,
        BasicType.MAT3X4,
        BasicType.MAT4X2,
        BasicType.MAT4X3,
        BasicType.MAT4X4
    }) {

      if (!SupportedTypes.supported(type, shadingLanguageVersion)) {
        continue;
      }

      for (UnOp op : Arrays.asList(UnOp.MINUS, UnOp.PRE_DEC, UnOp.POST_DEC, UnOp.PLUS, UnOp.PRE_INC,
            UnOp.POST_INC)) {

        if (op == UnOp.MINUS) {
          if (Arrays.asList(BasicType.UINT, BasicType.UVEC2, BasicType.UVEC3, BasicType.UVEC4)
                .contains(type)) {
            // TODO: revisit - not clear to me from above text whether unsigned should
            // support negation, but seems reasonable for this to be disallowed, and
            // the Intel graphics driver does not accept it
            continue;
          }
        }

        addTemplate(templates, new UnaryExprTemplate(type, type, op));
      }
    }

    /*
       The relational operators greater than (>), less than (<), greater than or equal (>=), and
       less than or equal (<=) operate only on scalar integer and scalar floating-point expressions.
       The result is scalar Boolean. Either the operands' types must match, or the conversions from
       section 4.1.10 "Implicit Conversions" will be applied to obtain matching types. To do
       component-wise relational comparisons on vectors, use the built-in functions lessThan,
       lessThanEqual, greaterThan, and greaterThanEqual.
    */

    for (BasicType type : Arrays.asList(BasicType.FLOAT, BasicType.UINT, BasicType.INT)) {
      if (type == BasicType.UINT && !shadingLanguageVersion.supportedUnsigned()) {
        continue;
      }
      for (BinOp op : Arrays.asList(BinOp.GT, BinOp.GE, BinOp.LT, BinOp.LT)) {
        addTemplate(templates, new BinaryExprTemplate(type, type, BasicType.BOOL, op));
      }
    }

    /*
      The equality operators equal (==), and not equal (!=) operate on all types (except aggregates
      that contain opaque types). They result in a scalar Boolean. If the operand types do not
      match, then there must be a conversion from section 4.1.10 "Implicit Conversions" applied to
      one operand that can make them match, in which case this conversion is done. For vectors,
      matrices, structures, and arrays, all components, members, or elements of one operand must
      equal the corresponding components, members, or elements in the other operand for the operands
      to be considered equal. To get a vector of component-wise equality results for vectors, use
      the built-in functions equal and notEqual.
    */

    for (BasicType type : supportedBasicTypes(shadingLanguageVersion)) {
      addTemplate(templates, new BinaryExprTemplate(type, type, BasicType.BOOL, BinOp.EQ));
      addTemplate(templates, new BinaryExprTemplate(type, type, BasicType.BOOL, BinOp.NE));
    }

    /*
      The logical binary operators and (&&), or ( || ), and exclusive or (^^) operate only on two
      Boolean expressions and result in a Boolean expression. And (&&) will only evaluate the right
      hand operand if the left hand operand evaluated to true. Or ( || ) will only evaluate the
      right hand operand if the left hand operand evaluated to false. Exclusive or (^^) will always
      evaluate both operands.
    */
    for (BinOp op : Arrays.asList(BinOp.LAND, BinOp.LOR, BinOp.LXOR)) {
      addTemplate(templates,
            new BinaryExprTemplate(BasicType.BOOL, BasicType.BOOL, BasicType.BOOL, op));
    }

    /*
      The logical unary operator not (!). It operates only on a Boolean expression and results in a
      Boolean expression. To operate on a vector, use the built-in function not.
    */
    addTemplate(templates, new UnaryExprTemplate(BasicType.BOOL, BasicType.BOOL, UnOp.LNOT));

    /*
      The sequence ( , ) operator that operates on expressions by returning the type and value of
      the right-most expression in a comma separated list of expressions. All expressions are
      evaluated, in order, from left to right.
      */
    if (GenerationParams.COMMA_OPERATOR_ENABLED) {
      for (BasicType type : supportedBasicTypes(shadingLanguageVersion)) {
        addTemplate(templates,
              new BinaryExprTemplate(supportedBasicTypes(shadingLanguageVersion),
                  type, type, BinOp.COMMA));
      }
    }

    /*
      The ternary selection operator (?:). It operates on three expressions (exp1 ? exp2 : exp3).
      This operator evaluates the first expression, which must result in a scalar Boolean. If the
      result is true, it selects to evaluate the second expression, otherwise it selects to evaluate
      the third expression. Only one of the second and third expressions is evaluated. The second
      and third expressions can be any type, as long their types match, or there is a conversion in
      section 4.1.10 "Implicit Conversions" that can be applied to one of the expressions to make
      their types match. This resulting matching type is the type of the entire expression.
    */
    for (BasicType type : supportedBasicTypes(shadingLanguageVersion)) {
      addTemplate(templates, new TernaryExprTemplate(type));
    }

    /*
      The one's complement operator (~). The operand must be of type signed or unsigned integer or
      integer vector, and the result is the one's complement of its operand; each bit of each
      component is complemented, including any sign bits.
    */

    if (shadingLanguageVersion.supportedBitwiseOperations()) {
      for (BasicType type : Arrays.asList(
            BasicType.UINT,
            BasicType.UVEC2,
            BasicType.UVEC3,
            BasicType.UVEC4,
            BasicType.INT,
            BasicType.IVEC2,
            BasicType.IVEC3,
            BasicType.IVEC4)) {
        addTemplate(templates, new UnaryExprTemplate(type, type, UnOp.BNEG));
      }
    }

    /*
      The shift operators (<<) and (>>). For both operators, the operands must be signed or unsigned
      integers or integer vectors. One operand can be signed while the other is unsigned. In all
      cases, the resulting type will be the same type as the left operand. If the first operand is a
      scalar, the second operand has to be a scalar as well. If the first operand is a vector, the
      second operand must be a scalar or a vector, and the result is computed component-wise. The
      result is undefined if the right operand is negative, or greater than or equal to the number
      of bits in the left expression's base type. The value of E1 << E2 is E1 (interpreted as a bit
      pattern) left-shifted by E2 bits. The value of E1 >> E2 is E1 right-shifted by E2 bit
      positions. If E1 is a signed integer, the right-shift will extend the sign bit. If E1 is an
      unsigned integer, the right-shift will zero-extend.
    */
    if (shadingLanguageVersion.supportedBitwiseOperations()) {
      for (BinOp op : Arrays.asList(BinOp.SHL, BinOp.SHR)) {
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UINT, BasicType.UINT, BasicType.UINT, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC2, BasicType.UVEC2, BasicType.UVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC2, BasicType.UINT, BasicType.UVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC3, BasicType.UVEC3, BasicType.UVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC3, BasicType.UINT, BasicType.UVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC4, BasicType.UVEC4, BasicType.UVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC4, BasicType.UINT, BasicType.UVEC4, op));

        addTemplate(templates,
              new BinaryExprTemplate(BasicType.INT, BasicType.INT, BasicType.INT, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC2, BasicType.IVEC2, BasicType.IVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC2, BasicType.INT, BasicType.IVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC3, BasicType.IVEC3, BasicType.IVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC3, BasicType.INT, BasicType.IVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC4, BasicType.IVEC4, BasicType.IVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC4, BasicType.INT, BasicType.IVEC4, op));
      }
    }

    /*
     * The bitwise operators and (&), exclusive-or (^), and inclusive-or (|). The operands must be
     * of type signed or unsigned integers or integer vectors. The operands cannot be vectors of
     * differing size; this is a compile-time error. If one operand is a scalar and the other a
     * vector, the scalar is applied component-wise to the vector, resulting in the same type as
     * the vector. The fundamental types of the operands (signed or unsigned) must match, and will
     * be the resulting fundamental type. For and (&), the result is the bitwise-and function of
     * the operands. For exclusive-or (^), the result is the bitwise exclusive-or function of the
     * operands. For inclusive-or (|), the result is the bitwise inclusive-or function of the
     * operands.
     */
    if (shadingLanguageVersion.supportedBitwiseOperations()) {
      for (BinOp op : Arrays.asList(BinOp.BAND, BinOp.BOR, BinOp.BXOR)) {

        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UINT, BasicType.UINT, BasicType.UINT, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC2, BasicType.UVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC3, BasicType.UVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UINT, BasicType.UVEC4, BasicType.UVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC2, BasicType.UINT, BasicType.UVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC2, BasicType.UVEC2, BasicType.UVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC3, BasicType.UINT, BasicType.UVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC3, BasicType.UVEC3, BasicType.UVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC4, BasicType.UINT, BasicType.UVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.UVEC4, BasicType.UVEC4, BasicType.UVEC4, op));

        addTemplate(templates,
              new BinaryExprTemplate(BasicType.INT, BasicType.INT, BasicType.INT, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.INT, BasicType.IVEC2, BasicType.IVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.INT, BasicType.IVEC3, BasicType.IVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.INT, BasicType.IVEC4, BasicType.IVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC2, BasicType.INT, BasicType.IVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC2, BasicType.IVEC2, BasicType.IVEC2, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC3, BasicType.INT, BasicType.IVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC3, BasicType.IVEC3, BasicType.IVEC3, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC4, BasicType.INT, BasicType.IVEC4, op));
        addTemplate(templates,
              new BinaryExprTemplate(BasicType.IVEC4, BasicType.IVEC4, BasicType.IVEC4, op));

      }
    }

    // Vector lookups
    {

      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC2, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC3, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC4, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC2, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC3, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.VEC4, false));

      if (shadingLanguageVersion.supportedUnsigned()) {
        addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.UVEC2, true));
        addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.UVEC3, true));
        addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.UVEC4, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.UVEC2, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.UVEC3, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.UVEC4, false));
      }

      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC2, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC3, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC4, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC2, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC3, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.IVEC4, false));

      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC2, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC3, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC4, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC2, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC3, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.BVEC4, false));

      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT2X2, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT3X3, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT4X4, true));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT2X2, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT3X3, false));
      addTemplate(templates, new VectorMatrixIndexExprTemplate(BasicType.MAT4X4, false));

      if (shadingLanguageVersion.supportedNonSquareMatrices()) {
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT2X3, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT2X4, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT3X2, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT3X4, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT4X2, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT4X3, true));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT2X3, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT2X4, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT3X2, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT3X4, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT4X2, false));
        addTemplate(templates,
              new VectorMatrixIndexExprTemplate(BasicType.MAT4X3, false));
      }

    }

    // Swizzles
    {

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.FLOAT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.VEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.VEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.VEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.FLOAT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.VEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.VEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.VEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.FLOAT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC4, false));

      if (shadingLanguageVersion.supportedUnsigned()) {
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UINT, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UVEC2, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UVEC3, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UVEC4, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UINT, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UVEC2, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UVEC3, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UVEC4, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UINT, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC2, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC3, false));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC4, false));
      }

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.INT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.IVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.IVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.IVEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.INT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.IVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.IVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.IVEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.INT, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC4, false));

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BOOL, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BVEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BOOL, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BVEC4, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BOOL, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC2, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC3, false));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC4, false));

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.FLOAT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC2, BasicType.VEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.FLOAT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.VEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC3, BasicType.VEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.FLOAT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.VEC4, BasicType.VEC4, true));

      if (shadingLanguageVersion.supportedUnsigned()) {
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UINT, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC2, BasicType.UVEC2, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UINT, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UVEC2, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC3, BasicType.UVEC3, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UINT, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC2, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC3, true));
        addTemplate(templates,
              new SwizzleExprTemplate(BasicType.UVEC4, BasicType.UVEC4, true));
      }

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.INT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC2, BasicType.IVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.INT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.IVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC3, BasicType.IVEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.INT, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.IVEC4, BasicType.IVEC4, true));

      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BOOL, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC2, BasicType.BVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BOOL, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC3, BasicType.BVEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BOOL, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC2, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC3, true));
      addTemplate(templates,
            new SwizzleExprTemplate(BasicType.BVEC4, BasicType.BVEC4, true));

    }

    return templates;

  }

  private static List<BasicType> supportedBasicTypes(
      ShadingLanguageVersion shadingLanguageVersion) {
    return BasicType.allBasicTypes()
        .stream()
        .filter(item -> SupportedTypes.supported(item, shadingLanguageVersion))
        .collect(Collectors.toList());
  }

  private static void addTemplate(List<IExprTemplate> templates, IExprTemplate template) {
    if (isBannedType(template.getResultType())) {
      return;
    }
    templates.add(template);
  }

  private static boolean isBannedType(Type type) {
    if (!GenerationParams.NON_SQUARE_MATRICES_ENABLED) {
      return Arrays.asList(BasicType.MAT2X3, BasicType.MAT2X4, BasicType.MAT3X2, BasicType.MAT3X4,
            BasicType.MAT4X2, BasicType.MAT4X3).contains(type);
    }
    return false;
  }

}
