/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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
import java.util.Collections;

public class LengthExpr extends Expr {

  private Expr receiver;

  public LengthExpr(Expr receiver) {
    setReceiver(receiver);
  }

  public Expr getReceiver() {
    return receiver;
  }

  public void setReceiver(Expr receiver) {
    checkNoTopLevelCommaExpression(Collections.singletonList(receiver));
    if (receiver == null) {
      throw new IllegalArgumentException("Length expression cannot have null receiver");
    }
    this.receiver = receiver;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitLengthExpr(this);
  }

  @Override
  public LengthExpr clone() {
    return new LengthExpr(receiver.clone());
  }

  @Override
  public Expr getChild(int index) {
    if (index == 0) {
      return receiver;
    }
    throw new IndexOutOfBoundsException("Index for LengthExpr must be 0");
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index == 0) {
      setReceiver(expr);
      return;
    }
    throw new IndexOutOfBoundsException("Index for LengthExpr must be 0");
  }

  @Override
  public int getNumChildren() {
    return 1;
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return receiver == candidateChild;
  }
}
