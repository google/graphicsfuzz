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

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
import java.util.Objects;
import java.util.Optional;

public class BooleanValue implements Value {

  private final Optional<Boolean> value;

  public BooleanValue(Optional<Boolean> value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }

    if (!(that instanceof BooleanValue)) {
      return false;
    }

    final BooleanValue thatBooleanValue = (BooleanValue) that;
    return this.value.equals(thatBooleanValue.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getType(), value);
  }

  @Override
  public Type getType() {
    return BasicType.BOOL;
  }

  @Override
  public boolean valueIsUnknown() {
    return !value.isPresent();
  }

  public Optional<Boolean> getValue() {
    return value;
  }

  @Override
  public Expr generateLiteral(LiteralFuzzer literalFuzzer) {
    if (valueIsUnknown()) {
      return literalFuzzer.fuzz(BasicType.BOOL).orElse(null);
    }
    return new BoolConstantExpr(value.get());
  }

  @Override
  public String toString() {
    return valueIsUnknown() ? "unknown_bool" : value.get().toString();
  }
}
