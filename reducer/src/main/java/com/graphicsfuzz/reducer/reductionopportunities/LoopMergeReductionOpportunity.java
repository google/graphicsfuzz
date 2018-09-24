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

import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.transformreduce.ReplaceLoopCounter;

public class LoopMergeReductionOpportunity extends AbstractReductionOpportunity {

  private final ForStmt firstLoop;
  private final ForStmt secondLoop;
  private final BlockStmt enclosingBlock;

  public LoopMergeReductionOpportunity(ForStmt firstLoop, ForStmt secondLoop,
      BlockStmt enclosingBlock,
      VisitationDepth depth) {
    super(depth);
    this.firstLoop = firstLoop;
    this.secondLoop = secondLoop;
    this.enclosingBlock = enclosingBlock;
  }

  @Override
  public void applyReductionImpl() {
    firstLoop.setCondition(secondLoop.getCondition());
    enclosingBlock.removeStmt(secondLoop);
    final String splitLoopCounter = ((DeclarationStmt) firstLoop.getInit())
        .getVariablesDeclaration().getDeclInfo(0).getName();
    int originalNameBeginIndex = Constants.SPLIT_LOOP_COUNTER_PREFIX.length();
    assert Character.isDigit(splitLoopCounter.charAt(originalNameBeginIndex));
    while (Character.isDigit(splitLoopCounter.charAt(originalNameBeginIndex))) {
      originalNameBeginIndex++;
    }
    final String mergedLoopCounter = splitLoopCounter.substring(originalNameBeginIndex);
    ReplaceLoopCounter.replaceLoopCounter(firstLoop, splitLoopCounter, mergedLoopCounter);
  }

  @Override
  public boolean preconditionHolds() {
    if (!enclosingBlock.getStmts().contains(secondLoop)) {
      return false;
    }
    if (!(firstLoop.getIncrement() instanceof UnaryExpr)) {
      return false;
    }
    if (!(((UnaryExpr) firstLoop.getIncrement()).getExpr() instanceof VariableIdentifierExpr)) {
      return false;
    }
    return true;
  }

}
