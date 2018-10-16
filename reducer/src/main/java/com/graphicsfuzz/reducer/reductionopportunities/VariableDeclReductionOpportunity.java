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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class VariableDeclReductionOpportunity extends AbstractReductionOpportunity {

  final VariableDeclInfo variableDeclInfo;
  final VariablesDeclaration variablesDeclaration; // The parent of variableDeclInfo

  VariableDeclReductionOpportunity(VariableDeclInfo variableDeclInfo,
                                   VariablesDeclaration variablesDeclaration,
                                   VisitationDepth depth) {
    super(depth);
    this.variableDeclInfo = variableDeclInfo;
    this.variablesDeclaration = variablesDeclaration;
  }

  @Override
  void applyReductionImpl() {
    if (!variablesDeclaration.getDeclInfos().contains(variableDeclInfo)) {
      // The declaration must have been removed by another reduction opportunity
      return;
    }
    for (int i = 0; i < variablesDeclaration.getNumDecls(); i++) {
      if (variablesDeclaration.getDeclInfo(i) == variableDeclInfo) {
        variablesDeclaration.removeDeclInfo(i);
        return;
      }
    }
    throw new RuntimeException("Should be unreachable.");
  }

  @Override
  public boolean preconditionHolds() {
    return variablesDeclaration.getDeclInfos().contains(variableDeclInfo);
  }

}
