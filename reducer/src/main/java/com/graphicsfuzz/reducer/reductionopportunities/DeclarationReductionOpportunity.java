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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

abstract class DeclarationReductionOpportunity extends AbstractReductionOpportunity {

  VariableDeclInfo variableDeclInfo;
  IParentMap parentMap;

  DeclarationReductionOpportunity(VariableDeclInfo variableDeclInfo, IParentMap parentMap,
      VisitationDepth depth) {
    super(depth);
    this.variableDeclInfo = variableDeclInfo;
    this.parentMap = parentMap;
  }

  @Override
  public final void applyReductionImpl() {
    IAstNode immediateParent = parentMap.getParent(variableDeclInfo);

    VariablesDeclaration vd = (VariablesDeclaration) immediateParent;

    if (!vd.getDeclInfos().contains(variableDeclInfo)) {
      // The declaration must have been removed by another reduction opportunity
      return;
    }

    boolean found = false;
    for (int i = 0; i < vd.getNumDecls(); i++) {
      if (vd.getDeclInfo(i) == variableDeclInfo) {
        vd.removeDeclInfo(i);
        found = true;
        break;
      }
    }
    assert found;
    if (vd.getNumDecls() != 0) {
      return;
    }
    removeVariablesDeclaration(vd);
  }

  abstract void removeVariablesDeclaration(VariablesDeclaration node);

  @Override
  public boolean preconditionHolds() {
    return parentMap.getParent(variableDeclInfo) instanceof VariablesDeclaration;
  }

}
