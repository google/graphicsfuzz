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
import com.graphicsfuzz.common.ast.type.Type;
import java.util.Optional;

public class ScopeEntry {

  private final Type type;

  // Represents the ParameterDecl that this variable came from, if one exists. If there is no such
  // object, the optional is empty.
  private final Optional<ParameterDecl> parameterDecl;

  // Represents the VariableDeclInfo that this variable came from, if one exists.  If there is no
  // such object (e.g. because the variable came from a parameter, or was made up for purposes of
  // fuzzing, or some such, the optional is empty.
  private final Optional<VariableDeclInfo> variableDeclInfo;

  // Represents the list of variable declarations that this variable came from, if one exists.
  // If there is no such object, the optional is empty.
  private final Optional<VariablesDeclaration> variablesDeclaration;

  // Represents the interface block that this variable is part of, if one exists. If there is no
  // such object, the optional is empty.
  private final Optional<InterfaceBlock> interfaceBlock;

  private ScopeEntry(Type type, Optional<ParameterDecl> parameterDecl,
                     Optional<VariableDeclInfo> variableDeclInfo,
                     Optional<VariablesDeclaration> variablesDecl,
                     Optional<InterfaceBlock> interfaceBlock) {
    this.type = type;
    this.parameterDecl = parameterDecl;
    this.variableDeclInfo = variableDeclInfo;
    this.variablesDeclaration = variablesDecl;
    this.interfaceBlock = interfaceBlock;
  }

  public ScopeEntry(Type type,
      VariableDeclInfo variableDeclInfo,
      VariablesDeclaration variablesDecl) {
    this(type, Optional.empty(), Optional.of(variableDeclInfo), Optional.of(variablesDecl),
        Optional.empty());
  }

  public ScopeEntry(Type type) {
    this(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public ScopeEntry(Type type, ParameterDecl parameterDecl) {
    this(type, Optional.of(parameterDecl), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public ScopeEntry(Type type, InterfaceBlock interfaceBlock) {
    this(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(interfaceBlock));
  }

  public Type getType() {
    return type;
  }

  public VariableDeclInfo getVariableDeclInfo() {
    return variableDeclInfo.get();
  }

  public boolean hasVariableDeclInfo() {
    return variableDeclInfo.isPresent();
  }

  public VariablesDeclaration getVariablesDeclaration() {
    return variablesDeclaration.get();
  }

  public boolean hasVariablesDeclaration() {
    return variablesDeclaration.isPresent();
  }

  public ParameterDecl getParameterDecl() {
    return parameterDecl.get();
  }

  public boolean hasParameterDecl() {
    return parameterDecl.isPresent();
  }

  public InterfaceBlock getInterfaceBlock() {
    return interfaceBlock.get();
  }

  public boolean hasInterfaceBlock() {
    return interfaceBlock.isPresent();
  }

}
