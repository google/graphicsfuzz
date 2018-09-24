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

package com.graphicsfuzz.generator.fuzzer.templates;

import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VectorMatrixIndexExprTemplate extends AbstractExprTemplate {

  // An index into this argument is chosen at random on expression generation
  private final BasicType argType;
  private final boolean isLValue;

  public VectorMatrixIndexExprTemplate(BasicType argType, boolean isLValue) {
    assert BasicType.allVectorTypes().contains(argType) || BasicType.allMatrixTypes()
          .contains(argType);
    this.argType = argType;
    this.isLValue = isLValue;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();

    int index;
    if (BasicType.allVectorTypes().contains(argType)) {
      index = generator.nextInt(argType.getNumElements());
    } else {
      assert BasicType.allMatrixTypes().contains(argType);
      index = generator.nextInt(BasicType.numColumns(argType));
    }
    return new ArrayIndexExpr(args[0], new IntConstantExpr(String.valueOf(index)));

  }

  @Override
  public Type getResultType() {
    if (argType == BasicType.VEC2 || argType == BasicType.VEC3 || argType == BasicType.VEC4) {
      return BasicType.FLOAT;
    }
    if (argType == BasicType.UVEC2 || argType == BasicType.UVEC3 || argType == BasicType.UVEC4) {
      return BasicType.UINT;
    }
    if (argType == BasicType.IVEC2 || argType == BasicType.IVEC3 || argType == BasicType.IVEC4) {
      return BasicType.INT;
    }
    if (argType == BasicType.BVEC2 || argType == BasicType.BVEC3 || argType == BasicType.BVEC4) {
      return BasicType.BOOL;
    }
    if (argType == BasicType.MAT2X2 || argType == BasicType.MAT3X2 || argType == BasicType.MAT4X2) {
      return BasicType.VEC2;
    }
    if (argType == BasicType.MAT2X3 || argType == BasicType.MAT3X3 || argType == BasicType.MAT4X3) {
      return BasicType.VEC3;
    }
    if (argType == BasicType.MAT2X4 || argType == BasicType.MAT3X4 || argType == BasicType.MAT4X4) {
      return BasicType.VEC4;
    }

    throw new RuntimeException("Cannot work out result type for arg type " + argType);
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(argType))));
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0;
    return isLValue;
  }

  @Override
  public boolean isLValue() {
    return isLValue;
  }

  @Override
  public boolean isConst() {
    return false;
  }

  @Override
  public int getNumArguments() {
    return 1;
  }

  @Override
  protected String getTemplateName() {
    return "VECTOR_MATRIX_INDEX";
  }

}
