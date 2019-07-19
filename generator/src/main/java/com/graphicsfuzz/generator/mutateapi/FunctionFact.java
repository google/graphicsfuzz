package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import java.util.List;
import java.util.Optional;

public class FunctionFact {
  FunctionPrototype function;
  List<Optional<Value>> arguments;
  Value result;


  public FunctionFact(FunctionPrototype function, List<Optional<Value>> arguments, Value result) {
    this.function = function;
    this.arguments = arguments;
    this.result = result;
  }
}
