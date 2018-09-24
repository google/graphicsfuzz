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

public class ParenExpr extends Expr {

  private Expr expr;

  public ParenExpr(Expr expr) {
    this.expr = expr;
  }

  public Expr getExpr() {
    return expr;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitParenExpr(this);
  }

  @Override
  public ParenExpr clone() {
    return new ParenExpr(expr.clone());
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
    throw new IndexOutOfBoundsException("Index for ParenExpr must be 0");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      this.expr = expr;
      return;
    }
    throw new IndexOutOfBoundsException("Index for ParenExpr must be 0");
  }

  @Override
  public int getNumChildren() {
    return 1;
  }

}
