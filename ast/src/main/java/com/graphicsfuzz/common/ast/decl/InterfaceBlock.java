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

package com.graphicsfuzz.common.ast.decl;

import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InterfaceBlock extends Declaration {

  private final Optional<LayoutQualifierSequence> layoutQualifier;
  private final List<TypeQualifier> interfaceQualifiers;
  private final String structName;
  private final List<String> memberNames;
  private final Map<String, Type> memberTypes;
  private final Optional<String> instanceName;

  public InterfaceBlock(
      Optional<LayoutQualifierSequence> layoutQualifier,
      List<TypeQualifier> interfaceQualifiers,
      String structName,
      List<String> memberNames,
      List<Type> memberTypes,
      Optional<String> instanceName) {
    // Check that there are no repeated qualifiers
    assert interfaceQualifiers.size() == new HashSet<>(interfaceQualifiers).size() : "Interface "
        + "block qualifiers must not be repeated";

    // Check that there is only one qualifier specifying the kind of block this is
    final Set<TypeQualifier> allowedQualifiers =
        new HashSet<>(Arrays.asList(TypeQualifier.SHADER_INPUT, TypeQualifier.SHADER_OUTPUT,
            TypeQualifier.UNIFORM, TypeQualifier.BUFFER));
    allowedQualifiers.retainAll(interfaceQualifiers);
    assert allowedQualifiers.size() == 1 :
        "An interface block must have exactly one of the 'in', 'out', 'uniform' or 'buffer' "
            + "qualifiers";

    // A buffer block is allowed to have memory qualifiers
    if (allowedQualifiers.contains(TypeQualifier.BUFFER)) {
      allowedQualifiers.addAll(new HashSet<>(Arrays.asList(
          TypeQualifier.COHERENT,
          TypeQualifier.VOLATILE,
          TypeQualifier.RESTRICT,
          TypeQualifier.READONLY,
          TypeQualifier.WRITEONLY)));
    }

    // Check that there are no unexpected qualifiers
    assert allowedQualifiers.containsAll(interfaceQualifiers) :
        "Only certain qualifiers are allowed on an interface block";

    this.layoutQualifier = layoutQualifier;
    this.interfaceQualifiers = new ArrayList<>(interfaceQualifiers);
    this.structName = structName;
    this.memberNames = new ArrayList<>();
    this.memberNames.addAll(memberNames);
    this.memberTypes = new HashMap<>();
    for (int i = 0; i < memberNames.size(); i++) {
      this.memberTypes.put(memberNames.get(i), memberTypes.get(i));
    }
    this.instanceName = instanceName;
  }

  public List<Type> getMemberTypes() {
    return memberNames.stream().map(memberTypes::get).collect(Collectors.toList());
  }

  public List<String> getMemberNames() {
    return Collections.unmodifiableList(memberNames);
  }

  public boolean hasLayoutQualifierSequence() {
    return layoutQualifier.isPresent();
  }

  public LayoutQualifierSequence getLayoutQualifierSequence() {
    assert hasLayoutQualifierSequence();
    return layoutQualifier.get();
  }

  public List<TypeQualifier> getInterfaceQualifiers() {
    return Collections.unmodifiableList(interfaceQualifiers);
  }

  public String getStructName() {
    return structName;
  }

  public boolean hasIdentifierName() {
    return instanceName.isPresent();
  }

  public String getInstanceName() {
    return instanceName.get();
  }

  public Type getMemberType(String name) {
    return memberTypes.get(name);
  }

  public boolean isUniformBlock() {
    return interfaceQualifiers.contains(TypeQualifier.UNIFORM);
  }

  public boolean isShaderStorageBlock() {
    return interfaceQualifiers.contains(TypeQualifier.BUFFER);
  }

  public boolean isInputBlock() {
    return interfaceQualifiers.contains(TypeQualifier.SHADER_INPUT);
  }

  public boolean isOutputBlock() {
    return interfaceQualifiers.contains(TypeQualifier.SHADER_OUTPUT);
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitInterfaceBlock(this);
  }

  @Override
  public InterfaceBlock clone() {
    return new InterfaceBlock(layoutQualifier,
        interfaceQualifiers,
        structName,
        memberNames,
        memberNames.stream().map(memberTypes::get).map(Type::clone).collect(Collectors.toList()),
        instanceName);
  }

}
