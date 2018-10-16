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

import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VariablesDeclaration extends Declaration {

  private Type baseType;
  private List<VariableDeclInfo> declInfos;

  public VariablesDeclaration(Type baseType,
                              List<VariableDeclInfo> declInfos) {
    assert baseType != null;
    this.baseType = baseType;
    this.declInfos = new ArrayList<>();
    this.declInfos.addAll(declInfos);
  }

  public VariablesDeclaration(Type baseType, VariableDeclInfo declInfo) {
    this(baseType, Arrays.asList(declInfo));
  }

  /**
   * Constructs a variables declaration with no variables attached.  Useful for making a lone
   * struct.
   * @param baseType The base type for the empty variables declaration.
   */
  public VariablesDeclaration(Type baseType) {
    this(baseType, new ArrayList<>());
  }

  public Type getBaseType() {
    return baseType;
  }

  public void setBaseType(Type baseType) {
    this.baseType = baseType;
  }

  public VariableDeclInfo getDeclInfo(int index) {
    return declInfos.get(index);
  }

  public List<VariableDeclInfo> getDeclInfos() {
    return Collections.unmodifiableList(declInfos);
  }

  public void setDeclInfo(int index, VariableDeclInfo variableDeclInfo) {
    declInfos.set(index, variableDeclInfo);
  }

  public int getNumDecls() {
    return declInfos.size();
  }

  public void removeDeclInfo(int index) {
    declInfos.remove(index);
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitVariablesDeclaration(this);
  }

  @Override
  public VariablesDeclaration clone() {
    return new VariablesDeclaration(baseType.clone(),
        declInfos.stream().map(x -> x.clone()).collect(Collectors.toList()));
  }

}
