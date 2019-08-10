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

package com.graphicsfuzz.generator.knownvaluegeneration;

import com.graphicsfuzz.common.ast.decl.ParameterDecl;

/**
 * This class holds the information of a parameter of generated functions and its associated value.
 * For any generated function, we randomly decide how parameters should be and for each parameter
 * we create a parameter declaration fact and keep it in Fact Manager which later will be used by
 * the Expression Generator when generating an expression.
 */
public class ParameterDeclFact extends VariableFact {
  private final ParameterDecl parameterDecl;

  public ParameterDeclFact(ParameterDecl parameterDecl,
                           Value value) {
    super(value);
    this.parameterDecl = parameterDecl;
  }

  @Override
  public String getVariableName() {
    return parameterDecl.getName();
  }

}
