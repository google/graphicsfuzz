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
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import java.util.Arrays;

public class DefaultLayout extends Declaration {

  private LayoutQualifier layoutQualifier;
  private TypeQualifier typeQualifier;

  public DefaultLayout(LayoutQualifier layoutQualifier, TypeQualifier typeQualifier) {
    assert Arrays.asList(
        TypeQualifier.UNIFORM,
        TypeQualifier.BUFFER,
        TypeQualifier.SHADER_INPUT,
        TypeQualifier.SHADER_OUTPUT).contains(typeQualifier);
    this.layoutQualifier = layoutQualifier;
    this.typeQualifier = typeQualifier;
  }

  public LayoutQualifier getLayoutQualifier() {
    return layoutQualifier;
  }

  public TypeQualifier getTypeQualifier() {
    return typeQualifier;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitDefaultLayout(this);
  }

  @Override
  public DefaultLayout clone() {
    return new DefaultLayout(layoutQualifier, typeQualifier);
  }

}
