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
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.StructUtils;
import java.util.Arrays;
import java.util.List;

public class GlobalVariablesDeclarationReductionOpportunities
      extends ReductionOpportunitiesBase<GlobalVariablesDeclarationReductionOpportunity> {

  private final TranslationUnit tu;

  private GlobalVariablesDeclarationReductionOpportunities(TranslationUnit tu,
                                                           ReducerContext context) {
    super(tu, context);
    this.tu = tu;
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    if (variablesDeclaration.getNumDecls() == 0) {
      if (!StructUtils.declaresReferencedStruct(tu, variablesDeclaration)) {
        addOpportunity(new GlobalVariablesDeclarationReductionOpportunity(
            tu,
            variablesDeclaration,
            getVistitationDepth()));
      }
    }
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    // Block entry to function so that we stay global.
  }

  private static List<GlobalVariablesDeclarationReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    GlobalVariablesDeclarationReductionOpportunities finder =
        new GlobalVariablesDeclarationReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  static List<GlobalVariablesDeclarationReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

}
