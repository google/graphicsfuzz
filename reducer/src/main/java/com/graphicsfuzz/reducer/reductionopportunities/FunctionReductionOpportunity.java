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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

class FunctionReductionOpportunity extends AbstractReductionOpportunity {

  private TranslationUnit tu;
  private Declaration functionDefinitionOrPrototype;

  public FunctionReductionOpportunity(TranslationUnit tu,
                                      Declaration functionDefinitionOrPrototype,
                                      VisitationDepth depth) {
    super(depth);
    assert functionDefinitionOrPrototype instanceof FunctionDefinition
        || functionDefinitionOrPrototype instanceof FunctionPrototype;
    this.tu = tu;
    this.functionDefinitionOrPrototype = functionDefinitionOrPrototype;
  }

  @Override
  public void applyReductionImpl() {
    for (int i = 0; i < tu.getTopLevelDeclarations().size(); i++) {
      if (tu.getTopLevelDeclarations().get(i) == functionDefinitionOrPrototype) {
        tu.removeTopLevelDeclaration(i);
        return;
      }
    }
    throw new RuntimeException("Should be unreachable");
  }

  @Override
  public boolean preconditionHolds() {
    return tu.getTopLevelDeclarations().contains(functionDefinitionOrPrototype);
  }

}
