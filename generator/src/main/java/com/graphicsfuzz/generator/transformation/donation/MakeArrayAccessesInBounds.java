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
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.Typer;

public class MakeArrayAccessesInBounds extends StandardVisitor {

  private Typer typer;

  private MakeArrayAccessesInBounds(Typer typer) {
    this.typer = typer;
  }

  public static void makeInBounds(IAstNode node, Typer typer) {
    new MakeArrayAccessesInBounds(typer).visit(node);
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    Type type = typer.lookupType(arrayIndexExpr.getArray());
    if (type == null) {
      return;
    }
    type = type.getWithoutQualifiers();
    assert isArrayVectorOrMatrix(type);
    if (!staticallyInBounds(arrayIndexExpr.getIndex(), type)) {
      arrayIndexExpr.setIndex(new TernaryExpr(
            new BinaryExpr(
                  new BinaryExpr(
                        new ParenExpr(arrayIndexExpr.getIndex().clone()),
                        new IntConstantExpr("0"),
                        BinOp.GE),
                  new BinaryExpr(
                        new ParenExpr(arrayIndexExpr.getIndex().clone()),
                        new IntConstantExpr(getSize(type).toString()),
                        BinOp.LT),
                  BinOp.LAND),
            arrayIndexExpr.getIndex(),
            new IntConstantExpr("0"))
      );
    }
    super.visitArrayIndexExpr(arrayIndexExpr);
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
    if (!(index instanceof IntConstantExpr)) {
      return false;
    }
    Integer indexValue = Integer.parseInt(((IntConstantExpr) index).getValue());
    return indexValue >= 0 && indexValue < getSize(type);
  }

}
