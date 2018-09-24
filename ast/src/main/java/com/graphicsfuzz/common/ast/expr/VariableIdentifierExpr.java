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

public class VariableIdentifierExpr extends Expr {

  private String name;

  public VariableIdentifierExpr(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitVariableIdentifierExpr(this);
  }

  @Override
  public VariableIdentifierExpr clone() {
    return new VariableIdentifierExpr(name);
  }

  @Override
  public boolean hasChild(IAstNode child) {
    return false;
  }

  @Override
  public Expr getChild(int index) {
    throw new IndexOutOfBoundsException("VariableIdentifierExpr has no children");
  }

  @Override
  public void setChild(int index, Expr expr) {
    throw new IndexOutOfBoundsException("VariableIdentifierExpr has no children");
  }

  @Override
  public int getNumChildren() {
    return 0;
  }

}
