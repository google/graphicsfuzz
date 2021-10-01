/*
 * Copyright 2018 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class Scope {

  private final Map<String, ScopeEntry> variableMapping = new HashMap<>();
  private final Map<String, StructDefinitionType> structMapping = new HashMap<>();
  private final Scope parent;

  public Scope() {
    this.parent = null;
  }

  public Scope(Scope parent) {
    if (parent == null) {
      throw new IllegalArgumentException("Attempt to create scope with null parent.  Use Scope() "
          + "constructor to create a global scope.");
    }
    this.parent = parent;
  }

  public void add(String name, Type type) {
    checkNameTypeAndParam(name, type, Optional.empty());
    variableMapping.put(name, new ScopeEntry(type));
  }

  public void add(String name, Type type,
                  VariableDeclInfo declInfo,
                  VariablesDeclaration variablesDecl) {
    checkNameTypeAndParam(name, type, Optional.empty());
    variableMapping.put(name, new ScopeEntry(type, declInfo, variablesDecl));
  }

  public void add(String name, Type type, ParameterDecl parameterDecl) {
    checkNameTypeAndParam(name, type, Optional.of(parameterDecl));
    variableMapping.put(name, new ScopeEntry(type, parameterDecl));
  }

  public void add(String name, Type type, InterfaceBlock interfaceBlock) {
    checkNameTypeAndParam(name, type, Optional.empty());
    variableMapping.put(name, new ScopeEntry(type, interfaceBlock));
  }

  public void addStructDefinition(StructDefinitionType sdt) {
    assert sdt.hasStructNameType();
    structMapping.put(sdt.getStructNameType().getName(), sdt);
  }

  /**
   * Checks whether the given name occurs more than once in this scope and all of its ancestor
   * scopes.
   * @param name A variable name.
   * @return true if and only if the name is shadowed.
   */
  public boolean isShadowed(String name) {
    boolean seenNameAlready = false;
    for (Scope current = this; current != null; current = current.parent) {
      if (current.keys().contains(name)) {
        if (seenNameAlready) {
          return true;
        }
        seenNameAlready = true;
      }
    }
    return false;
  }

  /**
   * Look in current scope to see whether we have a struct definition type matching the struct name.
   * @param structName Name of struct type.
   * @return Corresponding struct definition, if found, otherwise null.
   */
  public StructDefinitionType lookupStructName(String structName) {
    if (structMapping.containsKey(structName)) {
      return structMapping.get(structName);
    }
    if (hasParent()) {
      return getParent().lookupStructName(structName);
    }
    return null;
  }

  private void checkNameTypeAndParam(String name, Type type,
        Optional<ParameterDecl> parameterDecl) {
    if (type == null) {
      throw new RuntimeException("Attempt to register a variable '" + name + "' with null type");
    }
    if (variableMapping.containsKey(name)) {
      throw new DuplicateVariableException(name);
    }
    if (parameterDecl.isPresent() && parent != null && parent.parent != null) {
      throw new RuntimeException("Parameters cannot be added to a deeply nested scope");
    }
    assert name != null;
  }

  public Scope getParent() {
    return parent;
  }

  public ScopeEntry lookupScopeEntry(String name) {
    if (variableMapping.containsKey(name)) {
      return variableMapping.get(name);
    }
    if (parent != null) {
      return parent.lookupScopeEntry(name);
    }
    return null;
  }

  public Type lookupType(String name) {
    ScopeEntry entry = lookupScopeEntry(name);
    if (entry == null) {
      return null;
    }
    return entry.getType();
  }

  public List<String> namesOfAllVariablesInScope() {
    List<String> result = new ArrayList<>();
    Scope scope = this;
    while (scope != null) {
      result.addAll(scope.keys());
      scope = scope.parent;
    }
    result.sort(String::compareTo);
    return Collections.unmodifiableList(result);
  }

  public List<String> namesOfAllStructDefinitionsInScope() {
    List<String> result = new ArrayList<>();
    Scope scope = this;
    while (scope != null) {
      result.addAll(scope.structMapping.keySet());
      scope = scope.parent;
    }
    result.sort(String::compareTo);
    return Collections.unmodifiableList(result);
  }

  public List<String> keys() {
    List<String> result = new ArrayList<>(variableMapping.keySet());
    result.sort(String::compareTo);
    return Collections.unmodifiableList(result);
  }

  public boolean hasParent() {
    return parent != null;
  }

  /**
   * Clones the scope, recursively cloning all parents, but does not deep-clone the mappings.
   * @return Cloned scope
   */
  public Scope shallowClone() {
    Scope result = hasParent() ? new Scope(parent.shallowClone()) : new Scope();
    for (Entry<String, ScopeEntry> entry : variableMapping.entrySet()) {
      result.variableMapping.put(entry.getKey(), entry.getValue());
    }
    for (Entry<String, StructDefinitionType> entry : structMapping.entrySet()) {
      result.structMapping.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * Removes and returns scope entry associated with the given name, if present.  Otherwise
   * delegates removal to parent scope, if one exists.  Otherwise returns null to indicate that
   * nothing was removed.
   * @param name Name of the key to be removed
   * @return Scope entry that has been removed, or null if no removal took place
   */
  public ScopeEntry remove(String name) {
    if (variableMapping.containsKey(name)) {
      return variableMapping.remove(name);
    }
    if (hasParent()) {
      return parent.remove(name);
    }
    return null;
  }

}
