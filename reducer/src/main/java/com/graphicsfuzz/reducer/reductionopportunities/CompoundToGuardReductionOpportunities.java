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
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Collections;
import java.util.List;

public class CompoundToGuardReductionOpportunities
      extends ReductionOpportunitiesBase<CompoundToGuardReductionOpportunity> {

  private CompoundToGuardReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
  }

  static List<CompoundToGuardReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Collections.emptyList(), ListConcat::concatenate);
  }

  private static List<CompoundToGuardReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    CompoundToGuardReductionOpportunities finder =
        new CompoundToGuardReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    addOpportunity(doStmt, doStmt.getCondition());
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    // We can only replace a for loop with its guard if it actually has a guard.
    // Furthermore, we require that the for loop does not have a declaration in its
    // initializer, as this declaration might be used in the guard (so that we would
    // need to preserve the declaration too if we are to keep the guard).
    if (forStmt.hasCondition() && !(forStmt.getInit() instanceof DeclarationStmt)) {
      addOpportunity(forStmt, forStmt.getCondition());
    }
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    addOpportunity(whileStmt, whileStmt.getCondition());
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    addOpportunity(ifStmt, ifStmt.getCondition());
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    super.visitSwitchStmt(switchStmt);
    addOpportunity(switchStmt, switchStmt.getExpr());
  }

  private void addOpportunity(Stmt compoundStmt,
      Expr guard) {
    if (allowedToReduceCompoundStmt(compoundStmt)) {
      addOpportunity(new CompoundToGuardReductionOpportunity(
          parentMap.getParent(compoundStmt), compoundStmt, guard,
          getVistitationDepth()));
    }
  }

}
