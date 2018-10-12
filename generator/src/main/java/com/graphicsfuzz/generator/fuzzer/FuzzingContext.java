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

package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FuzzingContext {

  private final List<FunctionPrototype> functions;

  private final List<StructDefinitionType> structs;

  private Scope currentScope;

  private int enclosingLoops;

  private FunctionPrototype enclosingFunction = null;

  public FuzzingContext(Scope currentScope) {
    this.functions = new ArrayList<>();
    this.structs = new ArrayList<>();
    this.currentScope = currentScope;
    this.enclosingLoops = 0;
  }

  public FuzzingContext() {
    this(new Scope(null));
  }

  public void addGlobal(String name, Type type) {
    findRootScope().add(name, type, Optional.empty());
  }

  private Scope findRootScope() {
    Scope candidate = currentScope;
    while (candidate.hasParent()) {
      candidate = candidate.getParent();
    }
    return candidate;
  }

  public void addLocal(String name, Type type) {
    assert currentScope.hasParent();
    currentScope.add(name, type, Optional.empty());
  }

  public void addParameter(ParameterDecl parameterDecl) {
    assert currentScope.hasParent();
    currentScope.add(parameterDecl.getName(), parameterDecl.getType(), Optional.of(parameterDecl));
  }

  public void addFunction(FunctionPrototype prototype) {
    functions.add(prototype);
  }

  public void addStruct(StructDefinitionType struct) {
    structs.add(struct);
  }

  public void enterScope() {
    Scope newScope = new Scope(currentScope);
    currentScope = newScope;
  }

  public void leaveScope() {
    currentScope = currentScope.getParent();
  }

  public boolean inLoop() {
    return enclosingLoops > 0;
  }

  public boolean belowBlockNestingDepth(int maxBlockNestingDepth) {
    return numNestedScopes() < maxBlockNestingDepth;
  }

  private int numNestedScopes() {
    int result = 0;
    Scope temp = currentScope;
    while (temp.hasParent()) {
      result++;
      temp = temp.getParent();
    }
    return result;
  }

  public FunctionPrototype getEnclosingFunction() {
    return enclosingFunction;
  }

  public void leaveFunction() {
    assert enclosingFunction != null;
    enclosingFunction = null;
  }

  public void enterFunction(FunctionPrototype prototype) {
    assert enclosingFunction == null;
    enclosingFunction = prototype;
  }

  public Scope getCurrentScope() {
    return currentScope;
  }

  public List<StructDefinitionType> getStructDeclarations() {
    return Collections.unmodifiableList(structs);
  }

  public List<FunctionPrototype> getFunctionPrototypes() {
    return Collections.unmodifiableList(functions);
  }

  public boolean hasEnclosingFunction() {
    return enclosingFunction != null;
  }
}
