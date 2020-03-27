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
import java.util.Collections;

public class UnaryExpr extends Expr {

  private Expr expr;
  private UnOp op;

  public UnaryExpr(Expr expr, UnOp op) {
    checkNoTopLevelCommaExpression(Collections.singletonList(expr));
    this.expr = expr;
    this.op = op;
  }

  public Expr getExpr() {
    return expr;
  }

  public UnOp getOp() {
    return op;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitUnaryExpr(this);
  }

  @Override
  public UnaryExpr clone() {
    return new UnaryExpr(expr.clone(), op);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == expr;
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return expr;
    }
    throw new IndexOutOfBoundsException("Index for UnaryExpr must be 0");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      this.expr = expr;
      return;
    }
    throw new IndexOutOfBoundsException("Index for UnaryExpr must be 0");
  }

  @Override
  public int getNumChildren() {
    return 1;
  }

}
