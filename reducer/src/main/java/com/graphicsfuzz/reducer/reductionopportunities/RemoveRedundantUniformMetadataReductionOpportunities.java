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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.PipelineInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RemoveRedundantUniformMetadataReductionOpportunities
    extends ReductionOpportunitiesBase<RemoveRedundantUniformMetadataReductionOpportunity> {

  private final PipelineInfo pipelineInfo;
  private List<String> redundantUniformNames;

  public RemoveRedundantUniformMetadataReductionOpportunities(TranslationUnit tu,
                                                              ReducerContext context,
                                                              PipelineInfo pipelineInfo) {
    super(tu, context);
    this.pipelineInfo = pipelineInfo;
  }

  static List<RemoveRedundantUniformMetadataReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context, shaderJob.getPipelineInfo()))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {
    redundantUniformNames = new ArrayList<>(pipelineInfo.getUniformNames());
    super.visitTranslationUnit(translationUnit);

    for (String uniformName : redundantUniformNames) {
      addOpportunity(new RemoveRedundantUniformMetadataReductionOpportunity(uniformName,
          pipelineInfo, getVistitationDepth()));
    }
  }

  private static List<RemoveRedundantUniformMetadataReductionOpportunity>
      findOpportunitiesForShader(TranslationUnit tu,
                               ReducerContext context,
                               PipelineInfo pipelineInfo) {
    RemoveRedundantUniformMetadataReductionOpportunities finder =
          new RemoveRedundantUniformMetadataReductionOpportunities(tu, context, pipelineInfo);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    // This prevents removing a uniform declared but not used.
    super.visitVariablesDeclaration(variablesDeclaration);
    for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
      removeRedundantUniformName(vdi.getName());
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final String name = variableIdentifierExpr.getName();
    removeRedundantUniformName(name);
  }

  private void removeRedundantUniformName(String name) {
    assert redundantUniformNames != null;
    redundantUniformNames.remove(name);
  }

}
