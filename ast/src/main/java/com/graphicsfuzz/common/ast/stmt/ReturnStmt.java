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

package com.graphicsfuzz.common.ast.stmt;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class ReturnStmt extends Stmt {

  private Expr expr;

  public ReturnStmt(Expr expr) {
    assert expr != null;
    this.expr = expr;
  }

  public ReturnStmt() {
    this.expr = null;
  }

  public Expr getExpr() {
    return expr;
  }

  public void setExpr(Expr expr) {
    this.expr = expr;
  }

  public boolean hasExpr() {
    return getExpr() != null;
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    assert child == expr;
    assert newChild instanceof Expr;
    this.expr = (Expr) newChild;
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == expr;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitReturnStmt(this);
  }

  @Override
  public ReturnStmt clone() {
    if (expr == null) {
      return new ReturnStmt();
    }
    return new ReturnStmt(expr.clone());
  }

}
