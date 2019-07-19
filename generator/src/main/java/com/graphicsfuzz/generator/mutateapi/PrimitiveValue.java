package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.List;
import java.util.Optional;

public class PrimitiveValue implements Value {

  BasicType basicType;
  List<Optional<Number>> data;

  public PrimitiveValue(BasicType basicType, List<Optional<Number>> data) {
    this.basicType = basicType;
    this.data = data;
  }

  @Override
  public BasicType getType() {
    return basicType;
  }

  @Override
  public List<Optional<Number>> getData() {
    return data;
  }
}
