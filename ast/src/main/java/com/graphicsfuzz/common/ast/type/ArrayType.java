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

import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.typing.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ArrayType extends UnqualifiedType {

  private Type baseType;
  private ArrayInfo arrayInfo;

  public ArrayType(Type baseType, ArrayInfo arrayInfo) {
    if (arrayInfo.getDimensionality() != 1) {
      throw new IllegalArgumentException("Array types should be 1-dimensional: multi-dimensional "
          + "arrays are typed via arrays with arrays as their base type.");
    }
    if (baseType instanceof QualifiedType) {
      throw new IllegalArgumentException("Qualifiers should be applied to an array type, not to "
          + "the array's base type.");
    }
    this.baseType = baseType;
    this.arrayInfo = arrayInfo;
  }

  public Type getBaseType() {
    return baseType;
  }

  public ArrayInfo getArrayInfo() {
    return arrayInfo;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayType(this);
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof ArrayType)) {
      return false;
    }
    final ArrayType thatArrayType = (ArrayType) that;
    if (!this.baseType.equals(thatArrayType.baseType)) {
      return false;
    }
    assert this.arrayInfo.getDimensionality() == 1;
    assert thatArrayType.arrayInfo.getDimensionality() == 1;
    if (this.arrayInfo.hasConstantSize(0) != thatArrayType.arrayInfo.hasConstantSize(0)) {
      return false;
    }
    if (this.arrayInfo.hasConstantSize(0)
        && !this.arrayInfo.getConstantSize(0)
        .equals(thatArrayType.arrayInfo.getConstantSize(0))) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseType, arrayInfo.hasConstantSize(0)
          ? arrayInfo.getConstantSize(0)
          : arrayInfo.hasConstantSize(0));
  }

  @Override
  public ArrayType clone() {
    return new ArrayType(baseType.clone(), arrayInfo.clone());
  }

  @Override
  public boolean hasCanonicalConstant(Scope scope) {
    return baseType.hasCanonicalConstant(scope) && arrayInfo.hasConstantSize(0);
  }

  @Override
  public Expr getCanonicalConstant(Scope scope) {
    final Expr baseTypeCanonicalConstant = baseType.getCanonicalConstant(scope);
    final List<Expr> components = new ArrayList<>();
    for (int i = 0; i < arrayInfo.getConstantSize(0); i++) {
      components.add(baseTypeCanonicalConstant.clone());
    }
    return new ArrayConstructorExpr(this.clone(), components);
  }

}
