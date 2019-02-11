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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.generator.mutateapi.Mutation;

public class ReplaceDeclInfoMutation implements Mutation {

  private final VariablesDeclaration variablesDeclaration;
  private final int declIndex;
  private final VariableDeclInfo variableDeclInfo;

  protected ReplaceDeclInfoMutation(VariablesDeclaration variablesDeclaration, int declIndex,
                                    VariableDeclInfo variableDeclInfo) {
    this.variablesDeclaration = variablesDeclaration;
    this.declIndex = declIndex;
    this.variableDeclInfo = variableDeclInfo;
  }

  @Override
  public void apply() {
    variablesDeclaration.setDeclInfo(declIndex, variableDeclInfo);
  }

}
