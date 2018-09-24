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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;

public class InlineInitializerReductionOpportunity extends AbstractReductionOpportunity {

  private final TranslationUnit tu;
  private final VariableDeclInfo declWhoseInitializerShouldBeInlined;

  InlineInitializerReductionOpportunity(
        TranslationUnit tu,
        VariableDeclInfo declWhoseInitializerShouldBeInlined,
        VisitationDepth depth) {
    super(depth);
    this.tu = tu;
    this.declWhoseInitializerShouldBeInlined = declWhoseInitializerShouldBeInlined;
  }

  @Override
  void applyReductionImpl() {
    final IParentMap parentMap = IParentMap.createParentMap(tu);
    new ScopeTreeBuilder() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final ScopeEntry se = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
        if (se == null
              || !se.hasVariableDeclInfo()
              || !(se.getVariableDeclInfo() == declWhoseInitializerShouldBeInlined)) {
          return;
        }
        parentMap.getParent(variableIdentifierExpr).replaceChild(
              variableIdentifierExpr,
              new ParenExpr(
                    ((ScalarInitializer) declWhoseInitializerShouldBeInlined.getInitializer())
                    .getExpr().clone()));
      }
    }.visit(tu);
  }

  @Override
  public boolean preconditionHolds() {
    if (!declWhoseInitializerShouldBeInlined.hasInitializer()) {
      return false;
    }
    if (!(declWhoseInitializerShouldBeInlined.getInitializer() instanceof ScalarInitializer)) {
      return false;
    }
    // The other precondition is that the relevant variable never occurs in an l-value
    // context, but that would be expensive to check and it is unlikely to have become
    // violated.
    return true;
  }

}
