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
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DestructifyReductionOpportunities extends ScopeTreeBuilder {

  private final TranslationUnit tu;
  private final List<DestructifyReductionOpportunity> opportunities;

  private DestructifyReductionOpportunities(TranslationUnit tu) {
    this.tu = tu;
    this.opportunities = new LinkedList<>();
  }

  static List<DestructifyReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(DestructifyReductionOpportunities::findOpportunitiesForShader)
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<DestructifyReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    DestructifyReductionOpportunities finder = new DestructifyReductionOpportunities(tu);
    finder.visit(tu);
    return finder.opportunities;
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (Util.isStructifiedDeclaration(declarationStmt)) {
      final DestructifyReductionOpportunity op = new DestructifyReductionOpportunity(
            declarationStmt,
            tu,
            currentBlock(),
            getVistitationDepth());
      if (op.preconditionHolds()) {
        opportunities.add(op);
      }
    }
  }

}
