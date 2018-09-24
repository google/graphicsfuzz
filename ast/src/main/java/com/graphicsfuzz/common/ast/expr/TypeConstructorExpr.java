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

public class TypeConstructorExpr extends Expr {

  private String type;
  private List<Expr> args;

  /**
   * Creates a type constructor expression for the given named type, with the given arguments.
   *
   * @param type Name of the type to be constructed
   * @param args Types of the arguments
   */
  public TypeConstructorExpr(String type, List<Expr> args) {
    assert type != null;
    this.type = type;
    this.args = new ArrayList<>();
    this.args.addAll(args);
  }

  public TypeConstructorExpr(String type, Expr... params) {
    this(type, Arrays.asList(params));
  }

  public String getTypename() {
    return type;
  }

  public void setTypename(String type) {
    this.type = type;
  }

  public List<Expr> getArgs() {
    return Collections.unmodifiableList(args);
  }

  public Expr getArg(int index) {
    return args.get(index);
  }

  /**
   * Removes the argument at the given index and returns it.
   *
   * @param index The index at which an argument should be removed
   * @return The removed argument
   */
  public Expr removeArg(int index) {
    return args.remove(index);
  }

  /**
   * Reveals how many arguments there are.
   *
   * @return Number of arguments
   */
  public int getNumArgs() {
    return args.size();
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitTypeConstructorExpr(this);
  }

  @Override
  public TypeConstructorExpr clone() {
    return new TypeConstructorExpr(type,
        args.stream().map(x -> x.clone()).collect(Collectors.toList()));
  }

  /**
   * Inserts an argument at the given index, moving existing arguments down one place.
   *
   * @param index The index at which insertion should take place
   * @param arg The argument to be inserted
   */
  public void insertArg(int index, Expr arg) {
    args.add(index, arg);
  }

  @Override
  public boolean hasChild(IAstNode candidateChild) {
    return args.contains(candidateChild);
  }

  @Override
  public Expr getChild(int index) {
    if (index < 0 || index >= args.size()) {
      throw new IndexOutOfBoundsException("TypeConstructorExpr has no child at index " + index);
    }
    return args.get(index);
  }

  @Override
  public void setChild(int index, Expr expr) {
    if (index < 0 || index >= args.size()) {
      throw new IndexOutOfBoundsException("TypeConstructorExpr has no child at index " + index);
    }
    args.set(index, expr);
  }

  @Override
  public int getNumChildren() {
    return args.size();
  }

}
