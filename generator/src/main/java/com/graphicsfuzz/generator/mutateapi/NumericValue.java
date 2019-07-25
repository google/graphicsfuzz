package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.sun.org.apache.xpath.internal.operations.Variable;
import java.util.Optional;

public class NumericValue implements Value {

  private final BasicType basicType;
  private final Optional<Number> value;

  public NumericValue(BasicType basicType, Optional<Number> value) {
    this.basicType = basicType;
    this.value = value;
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
