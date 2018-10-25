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
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;

public class MutationReductionOpportunities
      extends ReductionOpportunitiesBase<MutationReductionOpportunity> {

  private MutationReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
  }

  @Override
  void identifyReductionOpportunitiesForChild(IAstNode parent, Expr child) {
    if (!(parent instanceof Expr)) {
      // TODO: the code was written for the case where 'parent' is an Expr, hence this bail-out.
      // But it might be the case that things should work just fine if 'parent' is not an Expr.
      // So this should be re-visited.
      return;
    }
    final Expr parentExpr = (Expr) parent;
    if (MacroNames.isIdentity(child)) {
      addOpportunity(new MutationReductionOpportunity(parentExpr, child,
                  OpaqueFunctionType.IDENTITY,
                  getVistitationDepth()));
    } else if (MacroNames.isZero(child)) {
      addOpportunity(new MutationReductionOpportunity(parentExpr, child,
            OpaqueFunctionType.ZERO,
            getVistitationDepth()));
    } else if (MacroNames.isOne(child)) {
      addOpportunity(new MutationReductionOpportunity(parentExpr, child,
            OpaqueFunctionType.ONE,
            getVistitationDepth()));
    } else if (MacroNames.isFalse(child)) {
      addOpportunity(new MutationReductionOpportunity(parentExpr, child,
            OpaqueFunctionType.FALSE,
            getVistitationDepth()));
    } else if (MacroNames.isTrue(child)) {
      addOpportunity(new MutationReductionOpportunity(parentExpr, child,
            OpaqueFunctionType.TRUE,
            getVistitationDepth()));
    }
  }

  static List<MutationReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<MutationReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    MutationReductionOpportunities finder = new MutationReductionOpportunities(tu,
          context);
    finder.visit(tu);
    return finder.getOpportunities();
  }
}
