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

public class ArrayIndexExpr extends Expr {

  private Expr array;
  private Expr index;

  public ArrayIndexExpr(Expr array, Expr index) {
    // Motivation for this exception:
    // vec2 v;
    // v[0]; // fine
    // v + vec2(0.0)[0]; // not fine - the following was probably intended:
    // (v + vec2(0.0))[0]; // fine
    if (array instanceof BinaryExpr) {
      throw new IllegalArgumentException("Array index into binary expression "
          + array.getText() + " not allowed.");
    }
    this.array = array;
    this.index = index;
  }

  public Expr getArray() {
    return array;
  }

  public Expr getIndex() {
    return index;
  }

  public void setIndex(Expr index) {
    this.index = index;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayIndexExpr(this);
  }

  @Override
  public ArrayIndexExpr clone() {
    return new ArrayIndexExpr(array.clone(), index.clone());
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return candidateChild == array || candidateChild == index;
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return array;
    }
    if (index == 1) {
      return this.index;
    }
    throw new IndexOutOfBoundsException("Index for ArrayIndexExpr must be 0 or 1");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      array = expr;
      return;
    }
    if (index == 1) {
      this.index = expr;
      return;
    }
    throw new IndexOutOfBoundsException("Index for ArrayIndexExpr must be 0 or 1");
  }

  @Override
  public int getNumChildren() {
    return 2;
  }

}
