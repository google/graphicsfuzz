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
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.PruneUniforms;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.List;

public class InlineUniformReductionOpportunities extends SimplifyExprReductionOpportunities {

  public static List<SimplifyExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context, shaderJob.getPipelineInfo()))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu, ReducerContext context, PipelineInfo pipelineInfo) {
    final InlineUniformReductionOpportunities finder = new InlineUniformReductionOpportunities(
        tu, context, pipelineInfo);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private final PipelineInfo pipelineInfo;

  private InlineUniformReductionOpportunities(TranslationUnit tu,
                                              ReducerContext context,
                                              PipelineInfo pipelineInfo) {
    super(tu, context);
    this.pipelineInfo = pipelineInfo;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);

    // We only inline uniforms if we are not preserving semantics, if the current program point is
    // is dead code, or if the uniform is a live-injected variable.
    if (!(context.reduceEverywhere() || currentProgramPointIsDeadCode()
        || Constants.isLiveInjectedVariableName(variableIdentifierExpr.getName()))) {
      return;
    }

    final String name = variableIdentifierExpr.getName();
    final ScopeEntry se = getCurrentScope().lookupScopeEntry(name);
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
        && pipelineInfo.hasUniform(name)) {
      final BasicType basicType = (BasicType) se.getType().getWithoutQualifiers();
      addOpportunity(new SimplifyExprReductionOpportunity(
          parentMap.getParent(variableIdentifierExpr),
          PruneUniforms.getBasicTypeLiteralExpr(basicType,
              pipelineInfo.getArgs(name)),
          variableIdentifierExpr,
          getVistitationDepth()));
    }
  }

}
