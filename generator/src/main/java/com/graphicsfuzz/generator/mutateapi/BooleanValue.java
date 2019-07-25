package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import java.util.Optional;

public class BooleanValue implements Value {

  private final Optional<Boolean> value;
  private final BasicType type;

  public BooleanValue(Optional<Boolean> value) {
    this.value = value;
    this.type = BasicType.BOOL;
  }

  public BooleanValue(boolean isTrue) {
    this(Optional.of(isTrue));
  }

  @Override
  public boolean equals(Object that) {
    return that instanceof BooleanValue && equals((BooleanValue) that);
  }

  public boolean equals(BooleanValue that) {
    if (this == that) {
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
  public String toString() {
    return value.get() ? "true" : "false";
  }
}
