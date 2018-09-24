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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionCallExpr extends Expr {

  private String callee;
  private List<Expr> args;

  public FunctionCallExpr(String callee, List<Expr> args) {
    this.callee = callee;
    this.args = new ArrayList<>();
    this.args.addAll(args);
  }

  public FunctionCallExpr(String callee, Expr... args) {
    this(callee, Arrays.asList(args));
  }

  public String getCallee() {
    return callee;
  }

  public void setCallee(String callee) {
    this.callee = callee;
  }

  public List<Expr> getArgs() {
    return Collections.unmodifiableList(args);
  }

  public int getNumArgs() {
    return args.size();
  }

  public Expr getArg(int index) {
    return args.get(index);
  }

  public void setArg(int index, Expr expr) {
    args.set(index, expr);
  }

  public void removeArg(int index) {
    args.remove(index);
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitFunctionCallExpr(this);
  }

  @Override
  public FunctionCallExpr clone() {
    return new FunctionCallExpr(callee,
        args.stream().map(x -> x.clone()).collect(Collectors.toList()));
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return args.contains(candidateChild);
  }

  @Override
  public Expr getChild(int index) {
    if (index < 0 || index >= getNumArgs()) {
      throw new IndexOutOfBoundsException("FunctionCallExpr has no child at index " + index);
    }
    return getArg(index);
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index < 0 || index >= getNumArgs()) {
      throw new IndexOutOfBoundsException("FunctionCallExpr has no child at index " + index);
    }
    args.set(index, expr);
  }

  @Override
  public int getNumChildren() {
    return getNumArgs();
  }

}
