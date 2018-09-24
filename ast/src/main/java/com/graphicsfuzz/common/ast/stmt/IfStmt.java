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

public class IfStmt extends Stmt {

  private Expr condition;
  private Stmt thenStmt;
  private Stmt elseStmt;

  public IfStmt(Expr condition, Stmt thenStmt, Stmt elseStmt) {
    this.condition = condition;
    this.thenStmt = thenStmt;
    this.elseStmt = elseStmt;
  }

  public Expr getCondition() {
    return condition;
  }

  public Stmt getThenStmt() {
    return thenStmt;
  }

  public Stmt getElseStmt() {
    return elseStmt;
  }

  public boolean hasElseStmt() {
    return elseStmt != null;
  }

  public void setThenStmt(Stmt thenStmt) {
    this.thenStmt = thenStmt;
  }

  public void setElseStmt(Stmt elseStmt) {
    this.elseStmt = elseStmt;
  }

  public void setCondition(Expr condition) {
    this.condition = condition;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitIfStmt(this);
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (child == condition && newChild instanceof Expr) {
      setCondition((Expr) newChild);
      return;
    }
    if (child == thenStmt && newChild instanceof Stmt) {
      setThenStmt((Stmt) newChild);
      return;
    }
    if (child == elseStmt && newChild instanceof Stmt) {
      setElseStmt((Stmt) newChild);
      return;
    }
    throw new IllegalArgumentException();
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    assert candidateChild != null;
    return condition == candidateChild
          || thenStmt == candidateChild
          || elseStmt == candidateChild;
  }

  @Override
  public IfStmt clone() {
    return new IfStmt(condition.clone(), thenStmt.clone(),
        elseStmt == null ? null : elseStmt.clone());
  }

}
