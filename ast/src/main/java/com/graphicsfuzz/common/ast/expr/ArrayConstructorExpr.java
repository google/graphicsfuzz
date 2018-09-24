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
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayConstructorExpr extends Expr {

  private final ArrayType arrayType;
  private final List<Expr> args;

  public ArrayConstructorExpr(ArrayType arrayType, List<Expr> args) {
    this.arrayType = arrayType;
    this.args = args;
  }

  public ArrayType getArrayType() {
    return arrayType;
  }

  public List<Expr> getArgs() {
    return Collections.unmodifiableList(args);
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayConstructorExpr(this);
  }

  @Override
  public ArrayConstructorExpr clone() {
    List<Expr> newArgs = args.stream().map(Expr::clone).collect(Collectors.toList());
    return new ArrayConstructorExpr(arrayType.clone(), newArgs);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return args.contains(candidateChild);
  }

  @Override
  public Expr getChild(int index) {
    checkBounds(index);
    return args.get(index);
  }

  @Override
  public void setChild(int index, Expr expr) {
    checkBounds(index);
    args.set(index, expr);
  }

  @Override
  public int getNumChildren() {
    return args.size();
  }

  private void checkBounds(int index) {
    if (!(index >= 0 && index < getNumChildren())) {
      throw new IndexOutOfBoundsException("No child at index " + index);
    }
  }

}
