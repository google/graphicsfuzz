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

package com.graphicsfuzz.common.ast.expr;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.Arrays;

public class BinaryExpr extends Expr {

  private Expr lhs;
  private Expr rhs;
  private BinOp op;

  /**
   * Makes a binary expression from the given expressions and operator.
   *
   * @param lhs Left hand sub-expression
   * @param rhs Right had sub-expression
   * @param op Operator
   */
  public BinaryExpr(Expr lhs, Expr rhs, BinOp op) {
    assert lhs != null;
    assert rhs != null;
    if (op != BinOp.COMMA) {
      checkNoTopLevelCommaExpression(Arrays.asList(lhs, rhs));
    }
    this.lhs = lhs;
    this.rhs = rhs;
    this.op = op;
  }

  public Expr getLhs() {
    return lhs;
  }

  public void setLhs(Expr lhs) {
    this.lhs = lhs;
  }

  public Expr getRhs() {
    return rhs;
  }

  public void setRhs(Expr rhs) {
    this.rhs = rhs;
  }

  public BinOp getOp() {
    return op;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitBinaryExpr(this);
  }

  @Override
  public BinaryExpr clone() {
    return new BinaryExpr(lhs.clone(), rhs.clone(), op);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return lhs == candidateChild || rhs == candidateChild;
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return getLhs();
    }
    if (index == 1) {
      return getRhs();
    }
    throw new IndexOutOfBoundsException("Index for BinaryExpr must be 0 or 1");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      lhs = expr;
      return;
    }
    if (index == 1) {
      rhs = expr;
      return;
    }
    throw new IndexOutOfBoundsException("Index for BinaryExpr must be 0 or 1");
  }

  @Override
  public int getNumChildren() {
    return 2;
  }

}
