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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
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
    if (child instanceof ParenExpr) {
      // We need to be careful about removing parentheses, as this can change precedence.
      // We only remove parentheses if they are at the root of an expression, immediately under
      // other parentheses, or immediately under a function call.
      if (!isOkToRemoveParens(parent, (ParenExpr) child)) {
        return;
      }
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

  private boolean isOkToRemoveParens(IAstNode parent, ParenExpr child) {

    if (child.getExpr() instanceof ConstantExpr
        || child.getExpr() instanceof VariableIdentifierExpr
        || child.getExpr() instanceof FunctionCallExpr) {
      // It's fine to remove parentheses in cases such as (5), (x) and (sin(a)).
      return true;
    }

    if (!(parent instanceof Expr)) {
      // These are outer-most parentheses; fine to remove them.
      return true;
    }
    if (parent instanceof ParenExpr) {
      // These are parentheses within parentheses; fine to remove them.
      return true;
    }
    if (parent instanceof FunctionCallExpr) {
      // These are parentheses under a function call argument.  Fine to remove them *unless*
      // they enclose a use of the comma operator; e.g. we don't want to turn sin((a, b)) into
      // sin(a, b).
      if (child.getExpr()
          instanceof BinaryExpr && ((BinaryExpr) child.getExpr()).getOp() == BinOp.COMMA) {
        return false;
      }
      return true;
    }
    // Conservatively say that it is not OK to remove parentheses.  We could be more aggressive
    // with attention to operator precedence.
    return false;
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
