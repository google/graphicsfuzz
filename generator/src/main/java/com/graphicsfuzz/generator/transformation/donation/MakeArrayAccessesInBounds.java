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

package com.graphicsfuzz.generator.transformation.donation;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.util.Constants;

public class MakeArrayAccessesInBounds extends ScopeTrackingVisitor {

  private Typer typer;

  private MakeArrayAccessesInBounds(Typer typer) {
    this.typer = typer;
  }

  /**
   * Clamp array / vector / matrix accesses to ensure they will be in-bounds.
   * @param node AST node under which accesses should be made in bound.
   * @param typer Type info for the AST.
   * @param tu The translation unit in which node is contained.
   */
  public static void makeInBounds(IAstNode node, Typer typer, TranslationUnit tu) {
    new MakeArrayAccessesInBounds(typer).visit(node);
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    super.visitArrayIndexExpr(arrayIndexExpr);
    Type type = typer.lookupType(arrayIndexExpr.getArray());
    if (type == null) {
      return;
    }
    type = type.getWithoutQualifiers();
    assert isArrayVectorOrMatrix(type);
    if (!staticallyInBounds(arrayIndexExpr.getIndex(), type)) {
      Type indexType = typer.lookupType(arrayIndexExpr.getIndex());
      if (indexType == null) {
        return;
      }
      indexType = indexType.getWithoutQualifiers();
      assert indexType == BasicType.INT || indexType == BasicType.UINT;

      final Expr arraySize = indexType == BasicType.INT
          ? new IntConstantExpr(getSize(type).toString())
          : new UIntConstantExpr(getSize(type).toString() + "u");

      final Expr clampedIndexExpr =
          new FunctionCallExpr(indexType == BasicType.INT ? Constants.GLF_MAKE_IN_BOUNDS_INT :
              Constants.GLF_MAKE_IN_BOUNDS_UINT,
              arrayIndexExpr.getIndex(),
              arraySize);
      arrayIndexExpr.setIndex(clampedIndexExpr);

    }
  }

  private static Integer getSize(Type type) {
    assert isArrayVectorOrMatrix(type);
    if (type instanceof ArrayType) {
      return ((ArrayType) type).getArrayInfo().getSize();
    }
    if (BasicType.allVectorTypes().contains(type)) {
      return ((BasicType) type).getNumElements();
    }
    return ((BasicType) type).getNumColumns();
  }

  private static boolean isArrayVectorOrMatrix(Type type) {
    return type instanceof ArrayType
          || BasicType.allMatrixTypes().contains(type)
          || BasicType.allVectorTypes().contains(type);
  }

  private static boolean staticallyInBounds(Expr index, Type type) {
    if (!(index instanceof IntConstantExpr || index instanceof UIntConstantExpr)) {
      return false;
    }
    Integer indexValue;
    if (index instanceof IntConstantExpr) {
      indexValue = ((IntConstantExpr) index).getNumericValue();
      return indexValue >= 0 && indexValue < getSize(type);
    } else { // index instanceof UIntConstantExpr
      indexValue = ((UIntConstantExpr) index).getNumericValue();
      return indexValue < getSize(type);
    }
  }

}
