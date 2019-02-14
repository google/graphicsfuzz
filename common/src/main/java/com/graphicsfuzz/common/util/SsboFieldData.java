package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SsboFieldData {

  private final BasicType baseType;
  private final List<Number> data;

  public SsboFieldData(BasicType baseType, List<Number> data) {
    assert !data.isEmpty();
    assert (baseType.getElementType() == baseType.FLOAT && data.get(0) instanceof Float)
        || (baseType.getElementType() == baseType.INT && data.get(0) instanceof Integer);
    this.baseType = baseType;
    this.data = new ArrayList<>();
    this.data.addAll(data);
  }

  public BasicType getBaseType() {
    return baseType;
  }

  public List<Number> getData() {
    return Collections.unmodifiableList(data);
  }

}
