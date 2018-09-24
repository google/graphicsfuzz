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

import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class InterfaceBlock extends Declaration {

  private final Optional<LayoutQualifier> layoutQualifier;
  private final TypeQualifier interfaceQualifier;
  private final String structName;
  private final List<String> memberNames;
  private final List<Type> memberTypes;
  private final Optional<String> instanceName;

  public InterfaceBlock(
      Optional<LayoutQualifier> layoutQualifier,
      TypeQualifier interfaceQualifier,
      String structName,
      List<String> memberNames,
      List<Type> memberTypes,
      Optional<String> instanceName) {
    this.layoutQualifier = layoutQualifier;
    this.interfaceQualifier = interfaceQualifier;
    assert Arrays.asList(TypeQualifier.SHADER_INPUT,
        TypeQualifier.SHADER_OUTPUT,
        TypeQualifier.UNIFORM,
        TypeQualifier.BUFFER)
        .contains(interfaceQualifier);
    this.structName = structName;
    this.memberNames = new ArrayList<>();
    this.memberNames.addAll(memberNames);
    this.memberTypes = new ArrayList<>();
    this.memberTypes.addAll(memberTypes);
    this.instanceName = instanceName;
  }

  public InterfaceBlock(LayoutQualifier layoutQualifier,
      TypeQualifier interfaceQualifier, String name,
      String memberName,
      Type memberType,
      String instanceName) {
    this(Optional.of(layoutQualifier), interfaceQualifier,
        name, Arrays.asList(memberName), Arrays.asList(memberType), Optional.of(instanceName));
  }

  public List<Type> getMemberTypes() {
    return Collections.unmodifiableList(memberTypes);
  }

  public List<String> getMemberNames() {
    return Collections.unmodifiableList(memberNames);
  }

  public boolean hasLayoutQualifier() {
    return layoutQualifier.isPresent();
  }

  public LayoutQualifier getLayoutQualifier() {
    assert hasLayoutQualifier();
    return layoutQualifier.get();
  }

  public TypeQualifier getInterfaceQualifier() {
    return interfaceQualifier;
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
    for (int i = 0; i < memberNames.size(); i++) {
      if (memberNames.get(i).equals(name)) {
        return memberTypes.get(i);
      }
    }
    throw new RuntimeException("Unknown member " + name);
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitInterfaceBlock(this);
  }

  @Override
  public InterfaceBlock clone() {
    return new InterfaceBlock(layoutQualifier,
        interfaceQualifier,
        structName,
        memberNames,
        memberTypes.stream().map(item -> item.clone()).collect(Collectors.toList()),
        instanceName);
  }

}
