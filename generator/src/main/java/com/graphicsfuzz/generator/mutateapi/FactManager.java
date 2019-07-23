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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.Expr;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FactManager {

  // A parent fact manager whose facts this fact manager extends.
  private final FactManager prototype;

  private final Map<VariableDeclInfo, VariableFact> variableFacts;
  private final Map<FunctionPrototype, FunctionFact> functionFacts;

  public FactManager(FactManager prototype) {
    this.prototype = prototype;
    variableFacts = new HashMap<>();
    functionFacts = new HashMap<>();
  }

  public FactManager clone() {
    return new FactManager(this);
  }

  // TODO: Retrieve and return the fact if found
  public Optional<Expr> getFact(Value value) {
    new RuntimeException("Not yet implemented");
    for (VariableDeclInfo key : variableFacts.keySet()) {
      VariableFact variableFact = variableFacts.get(key);
    }
    for (FunctionPrototype key : functionFacts.keySet()) {
      FunctionFact functionFact = functionFacts.get(key);
    }
    // Looks up for the facts in the parent FactManager.
    if (prototype != null) {
      return prototype.getFact(value);
    }
    return Optional.empty();
  }

  public Map<VariableDeclInfo, VariableFact> getVariableFacts() {
    return Collections.unmodifiableMap(variableFacts);
  }

  public Map<FunctionPrototype, FunctionFact> getFunctionFacts() {
    return Collections.unmodifiableMap(functionFacts);
  }

  public void addFunctionFact(FunctionPrototype functionPrototype, FunctionFact functionFact) {
    functionFacts.put(functionPrototype, functionFact);
  }

  public void addVariableFact(VariableDeclInfo variableDeclInfo, VariableFact variableFact) {
    variableFacts.put(variableDeclInfo, variableFact);
  }
}
