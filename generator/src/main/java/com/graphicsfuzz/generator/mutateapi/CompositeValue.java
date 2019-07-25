package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.type.Type;
import java.util.List;

public class CompositeValue implements Value{
  private final Type type;
  private final List<Value> valueList;

  public CompositeValue(Type type, List<Value> valueList) {
    this.type = type;
    this.valueList = valueList;
  }

  @Override
  public Type getType() {
    return null;
  }

  @Override
  public boolean valueIsKnown() {
    return false;
  }


}
