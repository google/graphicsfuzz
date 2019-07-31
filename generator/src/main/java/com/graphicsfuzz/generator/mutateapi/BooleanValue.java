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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
import java.util.Optional;

public class BooleanValue implements Value {

  private final Optional<Boolean> value;
  private final BasicType type;

  public BooleanValue(Optional<Boolean> value) {
    this.value = value;
    this.type = BasicType.BOOL;
  }

  @Override
  public boolean equals(Object that) {
    return that instanceof BooleanValue && equals((BooleanValue) that);
  }

  public boolean equals(BooleanValue that) {
    if (this == that) {
      return true;
    }

    if (!this.valueIsKnown() && !that.valueIsKnown()) {
      return true;
    }

    return this.getValue() == that.getValue();
  }

  @Override
  public Type getType() {
    return type;
  }

  public Optional<Boolean> getValue() {
    return value;
  }

  @Override
  public boolean valueIsKnown() {
    return value.isPresent();
  }

  @Override
  public Expr generateLiteral(LiteralFuzzer literalFuzzer) {
    if (!valueIsKnown()) {
      return literalFuzzer.fuzz(type).orElse(null);
    }
    return new BoolConstantExpr(value.get());
  }


  @Override
  public String toString() {
    return value.get() ? "true" : "false";
  }
}
