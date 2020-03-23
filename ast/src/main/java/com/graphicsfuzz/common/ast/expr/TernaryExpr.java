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

public class TernaryExpr extends Expr {

  private Expr test;
  private Expr thenExpr;
  private Expr elseExpr;

  /**
   * Makes a ternary expression from the given test and options.
   * @param test Boolean to be tested
   * @param thenExpr Result if the boolean is true
   * @param elseExpr Result if the boolean is false
   */
  public TernaryExpr(Expr test, Expr thenExpr, Expr elseExpr) {
    // The 'test' and 'else' expressions are not allowed to be top-level instances of the comma
    // operator, but the 'then' expression is, e.g. 'a ? b, c : d' is legal.
    checkNoTopLevelCommaExpression(Arrays.asList(test, elseExpr));
    this.test = test;
    this.thenExpr = thenExpr;
    this.elseExpr = elseExpr;
  }

  public Expr getTest() {
    return test;
  }

  public Expr getThenExpr() {
    return thenExpr;
  }

  public Expr getElseExpr() {
    return elseExpr;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitTernaryExpr(this);
  }

  @Override
  public TernaryExpr clone() {
    return new TernaryExpr(test.clone(), thenExpr.clone(), elseExpr.clone());
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == test || candidateChild == thenExpr || candidateChild == elseExpr;
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return test;
    }
    if (index == 1) {
      return thenExpr;
    }
    if (index == 2) {
      return elseExpr;
    }
    throw new IndexOutOfBoundsException("Index for TernaryExpr must be 0, 1 or 2");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      test = expr;
      return;
    }
    if (index == 1) {
      thenExpr = expr;
      return;
    }
    if (index == 2) {
      elseExpr = expr;
      return;
    }
    throw new IndexOutOfBoundsException("Index for TernaryExpr must be 0, 1 or 2");
  }

  @Override
  public int getNumChildren() {
    return 3;
  }

}
