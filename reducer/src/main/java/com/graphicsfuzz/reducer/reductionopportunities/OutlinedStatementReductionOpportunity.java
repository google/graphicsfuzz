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

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.HashMap;
import java.util.Map;

public class OutlinedStatementReductionOpportunity extends AbstractReductionOpportunity {

  private final ExprStmt stmt;
  private final FunctionDefinition outlined;

  public OutlinedStatementReductionOpportunity(ExprStmt stmt, FunctionDefinition outlined,
      VisitationDepth depth) {
    super(depth);
    assert stmt.getExpr() instanceof BinaryExpr;
    assert outlined.getBody().getNumStmts() == 1;
    assert outlined.getBody().getStmt(0) instanceof ReturnStmt;
    this.stmt = stmt;
    this.outlined = outlined;
  }

  @Override
  public void applyReductionImpl() {
    final BinaryExpr assignment = (BinaryExpr) stmt.getExpr();
    final Expr expr = ((ReturnStmt) outlined.getBody().getStmt(0)).getExpr().clone();

    Map<String, Expr> paramReplacement = new HashMap<>();
    for (int i = 0; i < outlined.getPrototype().getNumParameters(); i++) {
      Expr actualParam = ((FunctionCallExpr) assignment.getRhs()).getArg(i);
      assert actualParam != null;
      paramReplacement.put(outlined.getPrototype().getParameter(i).getName(),
          actualParam);
    }
    assert assignment.getOp() == BinOp.ASSIGN;
    stmt.setExpr(new BinaryExpr(assignment.getLhs(),
        applySubstitutionDestructive(expr, paramReplacement), BinOp.ASSIGN));
  }

  /**
   * Returns a subsituted expression, and may mutate the argument expression in the process.
   */
  private Expr applySubstitutionDestructive(Expr expr, Map<String, Expr> paramReplacement) {
    assert !paramReplacement.values().contains(null);
    if (expr instanceof VariableIdentifierExpr
        && paramReplacement.containsKey(((VariableIdentifierExpr) expr).getName())) {
      return paramReplacement.get(((VariableIdentifierExpr) expr).getName());
    }
    for (int i = 0; i < expr.getNumChildren(); i++) {
      Expr newChild = applySubstitutionDestructive(expr.getChild(i), paramReplacement);
      assert newChild != null;
      expr.setChild(i, newChild);
    }
    return expr;
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }
}
