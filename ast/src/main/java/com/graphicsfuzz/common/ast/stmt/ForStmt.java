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

public class ForStmt extends LoopStmt {

  private Stmt init;
  private Expr increment;

  public ForStmt(Stmt init, Expr condition, Expr increment, Stmt body) {
    super(condition, body);
    this.init = init;
    this.increment = increment;
  }

  @Override
  public boolean hasCondition() {
    return getCondition() != null;
  }

  /**
   * Reports whether a condition for the loop is present (it is not in e.g. "for(init; cond; )"
   *
   * @return Whether increment is present.
   */
  public boolean hasIncrement() {
    return getIncrement() != null;
  }

  public Stmt getInit() {
    return init;
  }

  public Expr getIncrement() {
    return increment;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitForStmt(this);
  }

  @Override
  public ForStmt clone() {
    return new ForStmt(init.clone(),
        hasCondition() ? getCondition().clone() : null,
        hasIncrement() ? increment.clone() : null,
        getBody().clone());
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (child == init) {
      init = (Stmt) newChild;
    } else if (child == increment) {
      increment = (Expr) newChild;
    } else {
      super.replaceChild(child, newChild);
    }
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == init
          || candidateChild == increment
          || super.hasChild(candidateChild);
  }

}
