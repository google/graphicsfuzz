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

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
import java.util.Optional;

public class NumericValue implements Value {

  private final BasicType basicType;
  private final Optional<Number> value;
  private boolean atGlobalScope;


  public NumericValue(BasicType basicType, Optional<Number> value, boolean atGlobalScope) {
    this.basicType = basicType;
    this.value = value;
    this.atGlobalScope = atGlobalScope;
  }

  public NumericValue(BasicType basicType, Optional<Number> value) {
    this(basicType, value, false);
  }

  public NumericValue(BasicType basicType, Number value) {
    this(basicType, Optional.of(value));
  }

  @Override
  public Type getType() {
    return basicType;
  }

  @Override
  public boolean valueIsKnown() {
    return value.isPresent();
  }

  @Override
  public boolean atGlobalScope() {
    return atGlobalScope;
  }

  @Override
  public void setGlobalScope(boolean atGlobalScope) {
    this.atGlobalScope = atGlobalScope;
  }

  @Override
  public Expr generateLiteral(LiteralFuzzer literalFuzzer) {
    if (valueIsKnown()) {
      if (basicType == BasicType.FLOAT) {
        return new FloatConstantExpr(value.get().toString());
      }
      if (basicType == BasicType.INT) {
        return new IntConstantExpr(value.get().toString());
      }
      if (basicType == BasicType.UINT) {
        return new UIntConstantExpr(value.get().toString() + "u");
      }
    }
    return literalFuzzer.fuzz(basicType).orElse(null);
  }

  public Optional<Number> getValue() {
    return value;
  }


  @Override
  public boolean equals(Object that) {
    return that instanceof NumericValue && equals((NumericValue) that);
  }

  public boolean equals(NumericValue that) {
    if (this == that) {
      return true;
    }
    if (this.getType() != that.getType()) {
      return false;
    }

    if (!valueIsKnown()) {
      return true;
    }

    if (this.getType() == BasicType.FLOAT || this.getType() == BasicType.INT) {
      return this.getValue().equals(that.getValue());
    }

    // TODO: handle unit case

    return false;
  }


  @Override
  public String toString() {
    return value.get().toString();
  }

}
