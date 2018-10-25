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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OutlinedStatementReductionOpportunities extends StandardVisitor {

  private final TranslationUnit tu;

  private List<OutlinedStatementReductionOpportunity> opportunities;

  private OutlinedStatementReductionOpportunities(TranslationUnit tu) {
    this.tu = tu;
    this.opportunities = new ArrayList<>();
  }

  static List<OutlinedStatementReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<OutlinedStatementReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    OutlinedStatementReductionOpportunities finder =
          new OutlinedStatementReductionOpportunities(tu);
    finder.visit(tu);
    return finder.opportunities;
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    super.visitExprStmt(exprStmt);
    if (!(exprStmt.getExpr() instanceof BinaryExpr)) {
      return;
    }
    final BinaryExpr be = (BinaryExpr) exprStmt.getExpr();
    if (be.getOp() != BinOp.ASSIGN) {
      return;
    }
    if (!(be.getRhs() instanceof FunctionCallExpr)) {
      return;
    }
    final String callee = ((FunctionCallExpr) be.getRhs()).getCallee();
    if (!callee.startsWith(Constants.OUTLINED_FUNCTION_PREFIX)) {
      return;
    }
    final List<FunctionDefinition> relevantFunctions = tu.getTopLevelDeclarations()
          .stream()
          .filter(item -> item instanceof FunctionDefinition)
          .map(item -> (FunctionDefinition) item)
          .filter(item -> item.getPrototype().getName().equals(callee))
          .collect(Collectors.toList());
    if (relevantFunctions.size() != 1) {
      throw new RuntimeException("Expected single function definition named " + callee);
    }

    FunctionDefinition relevantFunction = relevantFunctions.get(0);

    // For simplicitly, we only inline outlined functions comprising a single return statement.
    // This is what they look like when created, but they may get more complex due to other
    // injections.
    if (relevantFunction.getBody().getNumStmts() != 1) {
      return;
    }
    if (!(relevantFunction.getBody().getStmt(0) instanceof ReturnStmt)) {
      return;
    }
    opportunities.add(new OutlinedStatementReductionOpportunity(exprStmt,
          relevantFunctions.get(0), getVistitationDepth()));
  }

}
