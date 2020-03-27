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

import com.graphicsfuzz.common.ast.ChildDoesNotExistException;
import com.graphicsfuzz.common.ast.IAstNode;
import java.util.List;

public abstract class Expr implements IAstNode {

  @Override
  public abstract Expr clone();

  public abstract Expr getChild(int index);

  public abstract void setChild(int index, Expr expr);

  public abstract int getNumChildren();

  @Override
  public void replaceChild(IAstNode child, IAstNode newChild) {
    if (!(child instanceof Expr && newChild instanceof Expr)) {
      throw new IllegalArgumentException();
    }
    for (int i = 0; i < getNumChildren(); i++) {
      if (getChild(i) == child) {
        setChild(i, (Expr) newChild);
        return;
      }
    }
    throw new ChildDoesNotExistException(child, this);
  }

  public abstract boolean hasChild(IAstNode child);

  public static void checkNoTopLevelCommaExpression(List<Expr> args) {
    for (Expr arg : args) {
      if (arg instanceof BinaryExpr && ((BinaryExpr) arg).getOp() == BinOp.COMMA) {
        throw new IllegalArgumentException("Invalid use of comma expression.");
      }
    }
  }

}
