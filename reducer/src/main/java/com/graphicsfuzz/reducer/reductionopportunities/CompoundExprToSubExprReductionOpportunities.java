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
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;

public class CompoundExprToSubExprReductionOpportunities
      extends SimplifyExprReductionOpportunities {

  private CompoundExprToSubExprReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
  }

  @Override
  void identifyReductionOpportunitiesForChild(IAstNode parent, Expr child) {
    if (!allowedToReduceExpr(parent, child)) {
      return;
    }
    if (inLValueContext()) {
      return;
    }
    final Type resultType = typer.lookupType(child);
    if (resultType == null) {
      return;
    }
    for (int i = 0; i < child.getNumChildren(); i++) {
      final Expr subExpr = child.getChild(i);
      final Type subExprType = typer.lookupType(subExpr);
      if (subExprType == null) {
        continue;
      }
      if (!subExprType.getWithoutQualifiers().equals(resultType.getWithoutQualifiers())) {
        continue;
      }
      addOpportunity(new SimplifyExprReductionOpportunity(
                  parent,
                  subExpr,
                  child,
                  // We mark this as deeper since we would prefer to reduce the root expression
                  // to a constant.
                  getVistitationDepth().deeper()));
    }
  }

  static List<SimplifyExprReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    CompoundExprToSubExprReductionOpportunities finder =
          new CompoundExprToSubExprReductionOpportunities(tu,
                context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

}
