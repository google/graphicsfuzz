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

package com.graphicsfuzz.common.ast.decl;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class Initializer implements IAstNode {

  private Expr expr;

  public Initializer(Expr expr) {
    this.expr = expr;
  }

  public Expr getExpr() {
    return expr;
  }

  public void setExpr(Expr expr) {
    this.expr = expr;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitInitializer(this);
  }

  @Override
  public Initializer clone() {
    return new Initializer(expr.clone());
  }

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (!(child == expr && newChild instanceof Expr)) {
      throw new IllegalArgumentException();
    }
    setExpr((Expr) newChild);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == expr;
  }

}
