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
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class LoopMergeReductionOpportunities extends ScopeTreeBuilder {

  private final List<LoopMergeReductionOpportunity> opportunities;

  private LoopMergeReductionOpportunities() {
    this.opportunities = new LinkedList<>();
  }

  static List<LoopMergeReductionOpportunity> findOpportunities(
        ShaderJob shaderJob,
        ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<LoopMergeReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu) {
    LoopMergeReductionOpportunities finder = new LoopMergeReductionOpportunities();
    finder.visit(tu);
    return finder.opportunities;
  }

  @Override
  public void visitBlockStmt(BlockStmt block) {
    enterBlockStmt(block);
    checkForLoopMergeReductionOpportunities(block);
    for (Stmt child : block.getStmts()) {
      visit(child);
    }
    leaveBlockStmt(block);
  }

  private void checkForLoopMergeReductionOpportunities(BlockStmt block) {
    Optional<Stmt> prev = Optional.empty();
    for (Stmt current : block.getStmts()) {
      if (prev.isPresent() && canMergeLoops(prev.get(), current)) {
        opportunities.add(new LoopMergeReductionOpportunity((ForStmt) prev.get(), (ForStmt) current,
              block, getVistitationDepth()));
      }
      prev = Optional.of(current);
    }

  }

  private boolean canMergeLoops(Stmt first, Stmt second) {
    if (!(first instanceof ForStmt && second instanceof ForStmt)) {
      return false;
    }
    ForStmt firstLoop = (ForStmt) first;
    ForStmt secondLoop = (ForStmt) second;
    Optional<String> commonLoopCounter = checkForCommonLoopCounter(firstLoop, secondLoop);
    if (!commonLoopCounter.isPresent()) {
      return false;
    }
    if (!commonLoopCounter.get().startsWith(Constants.SPLIT_LOOP_COUNTER_PREFIX)) {
      return false;
    }
    if (!hasRegularLoopGuard(firstLoop, commonLoopCounter.get())) {
      return false;
    }
    if (!hasRegularLoopGuard(secondLoop, commonLoopCounter.get())) {
      return false;
    }

    final Integer firstLoopEnd = new Integer(((IntConstantExpr)
          ((BinaryExpr) firstLoop.getCondition()).getRhs()).getValue());

    final BinOp firstLoopOp = ((BinaryExpr) firstLoop.getCondition()).getOp();

    final Integer secondLoopStart = new Integer(((IntConstantExpr) ((ScalarInitializer)
          ((DeclarationStmt) secondLoop.getInit()).getVariablesDeclaration().getDeclInfo(0)
                .getInitializer()).getExpr()).getValue());

    assert firstLoopOp == BinOp.LT || firstLoopOp == BinOp.GT
          : "Unexpected operator in split loops.";

    return firstLoopEnd.equals(secondLoopStart);

  }

  private boolean hasRegularLoopGuard(ForStmt loop, String counterName) {
    if (!(loop.getCondition() instanceof BinaryExpr)) {
      return false;
    }
    final BinaryExpr guard = (BinaryExpr) loop.getCondition();
    if (!(guard.getOp() == BinOp.LT || guard.getOp() == BinOp.GT)) {
      return false;
    }
    if (!(guard.getLhs() instanceof VariableIdentifierExpr)) {
      return false;
    }
    if (!(guard.getRhs() instanceof IntConstantExpr)) {
      return false;
    }
    return ((VariableIdentifierExpr) guard.getLhs()).getName().equals(counterName);
  }

  private Optional<String> checkForCommonLoopCounter(ForStmt firstLoop, ForStmt secondLoop) {
    Optional<String> firstLoopCounter = extractLoopCounter(firstLoop);
    Optional<String> secondLoopCounter = extractLoopCounter(secondLoop);
    return firstLoopCounter.isPresent() && secondLoopCounter.isPresent()
          && firstLoopCounter.get().equals(secondLoopCounter.get())
          ? firstLoopCounter
          : Optional.empty();
  }

  private Optional<String> extractLoopCounter(ForStmt loop) {
    if (!(loop.getInit() instanceof DeclarationStmt)) {
      return Optional.empty();
    }
    final DeclarationStmt init = (DeclarationStmt) loop.getInit();
    if (init.getVariablesDeclaration().getNumDecls() != 1) {
      return Optional.empty();
    }
    return Optional.of(init.getVariablesDeclaration().getDeclInfo(0).getName());
  }

}
