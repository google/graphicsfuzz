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

package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.typing.Scope;

public final class StructNameType extends UnqualifiedType {

  private String name;

  public StructNameType(String name) {
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
    visitor.visitStructNameType(this);
  }

  @Override
  public StructNameType clone() {
    return new StructNameType(name);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof StructNameType)) {
      return false;
    }
    return name.equals(((StructNameType) that).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public final boolean hasCanonicalConstant(Scope scope) {
    if (scope.lookupStructName(name) == null) {
      throw new RuntimeException("Attempt to check whether a struct has a canonical constant when"
          + " the struct is not in scope.");
    }
    return scope.lookupStructName(name).hasCanonicalConstant(scope);
  }

  @Override
  public final Expr getCanonicalConstant(Scope scope) {
    return scope.lookupStructName(name).getCanonicalConstant(scope);
  }

}
