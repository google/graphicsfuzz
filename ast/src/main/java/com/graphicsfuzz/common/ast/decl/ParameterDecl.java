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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class ParameterDecl implements IAstNode {

  private String name;
  private Type type;
  private ArrayInfo arrayInfo;

  public ParameterDecl(String name, Type type, ArrayInfo arrayInfo) {
    this.name = name;
    this.type = type;
    this.arrayInfo = arrayInfo;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public ArrayInfo getArrayInfo() {
    return arrayInfo;
  }

  public boolean hasArrayInfo() {
    return arrayInfo != null;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitParameterDecl(this);
  }

  @Override
  public ParameterDecl clone() {
    return new ParameterDecl(name, type.clone(), arrayInfo == null ? null : arrayInfo.clone());
  }

}
