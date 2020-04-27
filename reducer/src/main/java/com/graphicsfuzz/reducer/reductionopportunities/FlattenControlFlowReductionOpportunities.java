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

public class FlattenControlFlowReductionOpportunities
      extends ReductionOpportunitiesBase<AbstractReductionOpportunity> {

  // Used to assess whether code that references loop limiters can be flattened.
  private final LoopLimiterImpactChecker loopLimiterImpactChecker;

  private FlattenControlFlowReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.loopLimiterImpactChecker = new LoopLimiterImpactChecker(tu);
  }

  static List<AbstractReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<AbstractReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    FlattenControlFlowReductionOpportunities finder =
          new FlattenControlFlowReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    if (allowedToReduce(doStmt)) {
      addOpportunity(new FlattenDoWhileLoopReductionOpportunity(
          parentMap.getParent(doStmt),
          doStmt,
          getVistitationDepth()));
    }
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    if (allowedToReduce(forStmt)) {
      addOpportunity(new FlattenForLoopReductionOpportunity(
          parentMap.getParent(forStmt),
          forStmt,
          getVistitationDepth()));
    }
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    if (allowedToReduce(whileStmt)) {
      addOpportunity(new FlattenWhileLoopReductionOpportunity(
          parentMap.getParent(whileStmt),
          whileStmt,
          getVistitationDepth()));
    }
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    if (allowedToReduce(ifStmt)) {
      addOpportunity(new FlattenConditionalReductionOpportunity(
          parentMap.getParent(ifStmt),
          ifStmt,
          true,
          getVistitationDepth()));
      if (ifStmt.hasElseStmt()) {
        addOpportunity(new FlattenConditionalReductionOpportunity(
            parentMap.getParent(ifStmt),
            ifStmt,
            false,
            getVistitationDepth()));
      }
    }
  }

  private boolean allowedToReduce(Stmt compoundStmt) {
    if (compoundStmt instanceof LoopStmt) {
      final LoopStmt loopStmt = (LoopStmt) compoundStmt;
      if (ContainsTopLevelBreak.check(loopStmt.getBody())
          || ContainsTopLevelContinue.check(loopStmt.getBody())) {
        return false;
      }
    }

    return context.reduceEverywhere()
          || currentProgramPointIsDeadCode()
          || (StmtReductionOpportunities.isLiveCodeInjection(compoundStmt)
               && !isLoopLimiterCheck(compoundStmt))
          || SideEffectChecker.isSideEffectFree(compoundStmt, context.getShadingLanguageVersion(),
        shaderKind);
  }

  private boolean isLoopLimiterCheck(Stmt compoundStmt) {
    return compoundStmt instanceof IfStmt
          && loopLimiterImpactChecker.referencesNonRedundantLoopLimiter(compoundStmt,
                                                                        getCurrentScope());
  }

}
