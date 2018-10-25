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
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.PruneUniforms;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.util.Arrays;
import java.util.List;

public class InlineUniformReductionOpportunities extends SimplifyExprReductionOpportunities {

  public static List<SimplifyExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context, shaderJob.getUniformsInfo()))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu, ReducerContext context, UniformsInfo uniformsInfo) {
    final InlineUniformReductionOpportunities finder = new InlineUniformReductionOpportunities(
        tu, context, uniformsInfo);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private final UniformsInfo uniformsInfo;

  private InlineUniformReductionOpportunities(TranslationUnit tu,
                                              ReducerContext context,
                                              UniformsInfo uniformsInfo) {
    super(tu, context);
    this.uniformsInfo = uniformsInfo;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final String name = variableIdentifierExpr.getName();
    final ScopeEntry se = currentScope.lookupScopeEntry(name);
    if (se == null) {
      return;
    }
    if (se.getType() == null) {
      return;
    }
    if (!se.getType().hasQualifier(TypeQualifier.UNIFORM)) {
      return;
    }
    if (se.getType().getWithoutQualifiers() instanceof BasicType
        && uniformsInfo.containsKey(name)) {
      final BasicType basicType = (BasicType) se.getType().getWithoutQualifiers();
      addOpportunity(new SimplifyExprReductionOpportunity(
          parentMap.getParent(variableIdentifierExpr),
          PruneUniforms.getBasicTypeLiteralExpr(basicType,
              uniformsInfo.getArgs(name)),
          variableIdentifierExpr,
          getVistitationDepth()));
    }
  }

}
