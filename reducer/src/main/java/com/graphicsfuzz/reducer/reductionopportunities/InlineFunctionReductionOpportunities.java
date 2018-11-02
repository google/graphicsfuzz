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
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.inliner.Inliner;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InlineFunctionReductionOpportunities extends StandardVisitor {

  private static final int INLINE_NODE_LIMIT = 100; // Only inline relatively small functions

  private final List<InlineFunctionReductionOpportunity> opportunities;
  private final TranslationUnit tu;
  private final ReducerContext context;

  static List<InlineFunctionReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<InlineFunctionReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    InlineFunctionReductionOpportunities finder = new InlineFunctionReductionOpportunities(
        tu, context);
    finder.visit(tu);
    return finder.opportunities;
  }

  private InlineFunctionReductionOpportunities(TranslationUnit tu,
        ReducerContext context) {
    opportunities = new ArrayList<>();
    this.tu = tu;
    this.context = context;
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);
    if (allowedToInline(functionCallExpr)
          && Inliner.canInline(
              functionCallExpr, tu, context.getShadingLanguageVersion(), INLINE_NODE_LIMIT)) {
      opportunities.add(new InlineFunctionReductionOpportunity(
            functionCallExpr,
            tu,
            context.getShadingLanguageVersion(),
            context.getIdGenerator(),
            getVistitationDepth()));
    }
  }

  private boolean allowedToInline(FunctionCallExpr functionCallExpr) {
    return context.reduceEverywhere()
          || functionCallExpr.getCallee().startsWith(Constants.LIVE_PREFIX)
          || functionCallExpr.getCallee().startsWith(Constants.DEAD_PREFIX);
  }

}
