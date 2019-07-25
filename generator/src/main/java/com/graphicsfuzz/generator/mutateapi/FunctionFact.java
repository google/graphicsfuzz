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

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import java.util.List;
import java.util.Optional;

public class FunctionFact {

  private final FunctionPrototype prototype;
  private final List<Value> arguments;
  private final Value value;


  public FunctionFact(FunctionPrototype prototype, List<Value> arguments, Value value) {
    this.prototype = prototype;
    this.arguments = arguments;
    this.value = value;
  }

  public String getFunctionName() {
    return prototype.getName();
  }

  public List<Value> getArguments() {
    return arguments;
  }

  public int getNumArgs() {
    return arguments.size();
  }

  public Value getValue() {
    return value;
  }

}
