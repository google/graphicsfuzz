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

public class BoolConstantExpr extends ConstantExpr {

  private final boolean isTrue;

  public BoolConstantExpr(boolean isTrue) {
    this.isTrue = isTrue;
  }

  @Override
  public boolean hasChild(IAstNode child) {
    return false;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitBoolConstantExpr(this);
  }

  @Override
  public BoolConstantExpr clone() {
    return new BoolConstantExpr(isTrue);
  }

  @Override
  public String toString() {
    return isTrue ? "true" : "false";
  }

  public boolean getIsTrue() {
    return isTrue;
  }

}
