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
import com.graphicsfuzz.common.transformreduce.ReplaceLoopCounter;
import com.graphicsfuzz.util.Constants;

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

    // It used to be the case that each split for loop counter had an associated integer id.
    // This turned out to be unnecessary and such ids are no longer generated.
    // So that the reducer can be run on existing generated shaders, logic to skip such an id
    // remains here.  It could be removed if it is not necessary to support existing generated
    // shaders.
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
      // Note: instanceof handles the case where there is no increment
      return false;
    }
    if (!(((UnaryExpr) firstLoop.getIncrement()).getExpr() instanceof VariableIdentifierExpr)) {
      return false;
    }
    return true;
  }

}
