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

public class SwitchStmt extends Stmt {

  private Expr expr;
  private BlockStmt body;

  public SwitchStmt(Expr expr, BlockStmt body) {
    this.expr = expr;
    this.body = body;
  }

  public Expr getExpr() {
    return expr;
  }

  public BlockStmt getBody() {
    return body;
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == expr || candidateChild == body;
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (child == expr) {
      expr = (Expr) newChild;
    } else if (child == body) {
      body = (BlockStmt) newChild;
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitSwitchStmt(this);
  }

  @Override
  public Stmt clone() {
    return new SwitchStmt(expr.clone(), body.clone());
  }

}
