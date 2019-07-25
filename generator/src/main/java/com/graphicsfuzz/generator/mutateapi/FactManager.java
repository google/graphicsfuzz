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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class FactManager {

  // A parent fact manager whose facts this fact manager extends.
  private final FactManager prototype;

  private final Map<Value, List<VariableFact>> variableFacts;
  private final Map<Value, List<FunctionFact>> functionFacts;

  public FactManager(FactManager prototype) {
    this.prototype = prototype;
    variableFacts = new HashMap<Value, List<VariableFact>>();
    functionFacts = new HashMap<Value, List<FunctionFact>>();

  }

  public FactManager clone() {
    return new FactManager(this);
  }


  public Map<Value, List<VariableFact>> getVariableFacts() {
    return Collections.unmodifiableMap(variableFacts);
  }

  public Optional<List<FunctionFact>> getFunctionFacts(Value value) {
    // TODO: Needs to retrieve parent fact manager function facts
    return functionFacts.entrySet()
        .stream()
        .filter(item -> item.getKey().equals(value))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  public Optional<List<VariableFact>> getVariableFacts(Value value) {
    // TODO: Needs to retrieve parent fact manager variable facts
    return variableFacts.entrySet()
        .stream()
        .filter(item -> item.getKey().equals(value))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  public Map<Value, List<FunctionFact>> getFunctionFacts() {
    return Collections.unmodifiableMap(functionFacts);
  }

  public void addVariableFact(Value value, VariableFact variableFact, boolean globalScope) {
    final List<VariableFact> newFacts = variableFacts.getOrDefault(value, new ArrayList<>());
    newFacts.add(variableFact);
    variableFacts.put(value, newFacts);

    // If globalScope is true, we recursively add variable fact to its ancestors.
    // By doing so, all fact managers in the chain will have access to the new global
    // variable given and its associated fact.
    if (prototype != null && globalScope) {
      prototype.addVariableFact(value, variableFact, globalScope);
    }
  }

  public void addFunctionFact(Value value, FunctionFact functionFact) {
    final List<FunctionFact> newFacts = functionFacts.getOrDefault(value, new ArrayList<>());
    newFacts.add(functionFact);
    functionFacts.put(value, newFacts);

    if (prototype != null) {
      prototype.addFunctionFact(value, functionFact);
    }
  }

}
