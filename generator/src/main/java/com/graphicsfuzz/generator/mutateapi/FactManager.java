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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  /**
   * Creates a new fact manager where this fact manager is used as a prototype. We basically call
   * this method only when generating a new function where the return fact manager is non global.
   *
   * @return new fact manager.
   */
  public FactManager newScope() {
    return new FactManager(this);
  }

  /**
   * Determines whether the fact manager is at the global scope.
   *
   * @return true if this manager has a prototype and false otherwise.
   */
  public boolean globalScope() {
    return prototype == null;
  }

  /**
   * Adds a new variable fact to the variable fact map of this fact manager.
   *
   * @param value        value which the given variable fact is representing.
   * @param variableFact a new variable fact that will be added into the map of variable facts.
   */
  public void addVariableFact(Value value, VariableFact variableFact) {
    final List<VariableFact> newFacts = variableFacts.getOrDefault(value, new ArrayList<>());
    newFacts.add(variableFact);
    variableFacts.put(value, newFacts);
  }

  /**
   * As function facts can only exist at the global scope, this method adds a new function
   * fact into the map of the function facts of the global scope fact manager.
   *
   * @param value        value which the given function fact is representing.
   * @param functionFact a new function fact that will be added into the map of function facts.
   */
  public void addFunctionFact(Value value, FunctionFact functionFact) {
    if (globalScope()) {
      final List<FunctionFact> newFacts = functionFacts.getOrDefault(value, new ArrayList<>());
      newFacts.add(functionFact);
      functionFacts.put(value, newFacts);
    } else {
      prototype.addFunctionFact(value, functionFact);
    }
  }

  /**
   * Retrieves a list of variable facts representing the value.
   *
   * @param value a value which the fact manager is asked to search in the variable fact map.
   * @return if value does not exist in the variable fact map, an empty list is returned. Otherwise,
   *     returns a list of variable facts that guarantees to provide the given value.
   */
  public List<VariableFact> getVariableFacts(Value value) {
    final List<VariableFact> result = new ArrayList<>();
    result.addAll(variableFacts.getOrDefault(value, Collections.emptyList()));
    if (!globalScope()) {
      result.addAll(prototype.getVariableFacts(value));
    }
    return result;
  }

  /**
   * Retrieves a list of function facts representing the value from the global scope fact
   * manager. If the value does not exist in the map, an empty list is returned.
   *
   * @param value a value which the fact manager is asked to search in the function fact map.
   * @return if value does not exist in the function fact map, an empty list is returned.
   *     Otherwise, returns a list of function facts that guarantees to provide the given value.
   */
  public List<FunctionFact> getFunctionFacts(Value value) {
    if (globalScope()) {
      return functionFacts.getOrDefault(value, Collections.emptyList());
    }
    return prototype.getFunctionFacts(value);
  }

}
