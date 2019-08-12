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

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;

/**
 * This class holds the information of the newly-generated variable and its associated Value.
 * Each time we declare a new variable, we create a variable declaration fact and keep it in Fact
 * Manager which later will be used by the Expression Generator when generating known value
 * expression.
 */
public class VariableDeclFact extends VariableFact {

  private final VariablesDeclaration variablesDeclaration;
  private final VariableDeclInfo variableDeclInfo;

  public VariableDeclFact(VariablesDeclaration variablesDeclaration,
                          VariableDeclInfo variableDeclInfo,
                          Value value) {
    super(value);
    this.variablesDeclaration = variablesDeclaration;
    this.variableDeclInfo = variableDeclInfo;
  }

  public String getVariableName() {
    return variableDeclInfo.getName();
  }

}
