package com.graphicsfuzz.generator.mutateapi;


import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.List;
import java.util.Optional;

public interface Value {

  BasicType getType();

  List<Optional<Number>> getData();

}
