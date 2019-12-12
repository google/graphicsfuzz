/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.knownvaluegeneration;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
import java.util.Objects;
import java.util.Optional;

public class NumericValue implements Value {

  private final BasicType basicType;
  private final Optional<Number> value;

  public NumericValue(BasicType basicType, Optional<Number> value) {
    this.basicType = basicType;
    this.value = value;
  }

  @Override
  public Type getType() {
    return basicType;
  }

  @Override
  public boolean valueIsUnknown() {
    return !value.isPresent();
  }

  @Override
  public Expr generateLiteral(LiteralFuzzer literalFuzzer) {
    if (valueIsUnknown()) {
      return literalFuzzer.fuzz(basicType).orElse(null);
    }
    if (basicType == BasicType.FLOAT) {
      return new FloatConstantExpr(value.get().toString());
    }
    if (basicType == BasicType.INT) {
      return new IntConstantExpr(value.get().toString());
    }
    if (basicType == BasicType.UINT) {
      return new UIntConstantExpr(value.get().toString() + "u");
    }
    throw new RuntimeException("Numeric value does not support the given type");
  }

  public Optional<Number> getValue() {
    return value;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }

    if (!(that instanceof NumericValue)) {
      return false;
    }
    final NumericValue thatNumericValue = (NumericValue) that;
    return this.basicType.equals(thatNumericValue.basicType)
        && this.value.equals(thatNumericValue.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(basicType, value);
  }

  @Override
  public String toString() {
    return valueIsUnknown() ? "unknown_numeric" : value.get().toString();
  }

}
