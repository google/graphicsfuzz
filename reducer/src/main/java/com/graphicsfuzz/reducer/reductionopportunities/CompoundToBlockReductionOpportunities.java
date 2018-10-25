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
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ContainsTopLevelBreak;
import com.graphicsfuzz.common.util.ContainsTopLevelContinue;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.SideEffectChecker;
import java.util.Arrays;
import java.util.List;

public class CompoundToBlockReductionOpportunities
      extends ReductionOpportunitiesBase<CompoundToBlockReductionOpportunity> {

  private int enclosingLiveCodeInjections;

  public CompoundToBlockReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.enclosingLiveCodeInjections = 0;
  }

  static List<CompoundToBlockReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<CompoundToBlockReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    CompoundToBlockReductionOpportunities finder =
          new CompoundToBlockReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    handleLoopStmt(doStmt);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    handleLoopStmt(forStmt);
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    handleLoopStmt(whileStmt);
  }

  private void handleLoopStmt(LoopStmt loopStmt) {
    if (ContainsTopLevelBreak.check(loopStmt.getBody())
          || ContainsTopLevelContinue.check(loopStmt.getBody())) {
      return;
    }
    if (MacroNames.isDeadByConstruction(loopStmt.getCondition())) {
      return;
    }
    addOpportunity(loopStmt, loopStmt.getBody());
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    if (MacroNames.isDeadByConstruction(ifStmt.getCondition())) {
      return;
    }
    addOpportunity(ifStmt, ifStmt.getThenStmt());
    if (ifStmt.hasElseStmt()) {
      addOpportunity(ifStmt, ifStmt.getElseStmt());
    }
  }

  private void addOpportunity(Stmt compoundStmt,
        Stmt childStmt) {
    if (allowedToReduce(compoundStmt)) {
      addOpportunity(new CompoundToBlockReductionOpportunity(
            parentMap.getParent(compoundStmt), compoundStmt, childStmt,
            getVistitationDepth()));
    }
  }

  @Override
  public void visitBlockStmt(BlockStmt block) {
    if (StmtReductionOpportunities.isLiveCodeInjection(block)) {
      enclosingLiveCodeInjections++;
    }
    super.visitBlockStmt(block);
    if (StmtReductionOpportunities.isLiveCodeInjection(block)) {
      enclosingLiveCodeInjections--;
    }
  }

  private boolean allowedToReduce(Stmt compoundStmt) {
    return context.reduceEverywhere()
          || injectionTracker.enclosedByDeadCodeInjection()
          || injectionTracker.underUnreachableSwitchCase()
          || (inLiveCodeInjection() && !isLoopLimiterCheck(compoundStmt))
          || enclosingFunctionIsDead()
          || SideEffectChecker.isSideEffectFree(compoundStmt, context.getShadingLanguageVersion());
  }

  private static boolean isLoopLimiterCheck(Stmt compoundStmt) {
    return compoundStmt instanceof IfStmt
          && StmtReductionOpportunities.referencesLoopLimiter(compoundStmt);
  }

  private boolean inLiveCodeInjection() {
    return enclosingLiveCodeInjections > 0;
  }

}
