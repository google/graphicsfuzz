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
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ExprToConstantReductionOpportunities extends SimplifyExprReductionOpportunities {

  private ExprToConstantReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
  }

  @Override
  void identifyReductionOpportunitiesForChild(IAstNode parent, Expr child) {
    if (allowedToReduceExpr(parent, child) && !inLValueContext()
          && typeIsReducibleToConst(typer.lookupType(child))
          && !isFullyReducedConstant(child, typer)) {
      addOpportunity(new SimplifyExprReductionOpportunity(
                  parent,
                  typer.lookupType(child).getCanonicalConstant(getCurrentScope()),
                  child,
                  getVistitationDepth()));
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
    ExprToConstantReductionOpportunities finder = new ExprToConstantReductionOpportunities(tu,
          context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private boolean typeIsReducibleToConst(Type type) {
    return type != null && type.hasCanonicalConstant(getCurrentScope());
  }

  private boolean isFullyReducedConstant(Expr expr, Typer typer) {
    final Type type = typer.lookupType(expr);
    if (type == null) {
      return false;
    }
    if (!type.hasCanonicalConstant(getCurrentScope())) {
      return false;
    }
    // To make the reduced shader as clean as possible, we try reducing every constant to
    // something textually equivalent to its canonical constant.
    return expr.getText()
        .equals(type.getCanonicalConstant(getCurrentScope()).getText());
  }

}
