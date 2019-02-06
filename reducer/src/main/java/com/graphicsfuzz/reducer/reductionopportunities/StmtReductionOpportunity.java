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
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public final class StmtReductionOpportunity extends AbstractReductionOpportunity {

  private final IAstNode parentOfBlockStmt;
  private final BlockStmt blockStmt;
  private final Stmt childOfBlockStmt;

  public StmtReductionOpportunity(IAstNode parentOfBlockStmt,
                                  BlockStmt blockStmt,
                                  Stmt childOfBlockStmt,
                                  VisitationDepth depth) {
    super(depth);
    this.parentOfBlockStmt = parentOfBlockStmt;
    this.blockStmt = blockStmt;
    this.childOfBlockStmt = childOfBlockStmt;
  }

  @Override
  public void applyReductionImpl() {
    for (int i = 0; i < blockStmt.getNumStmts(); i++) {
      if (childOfBlockStmt == blockStmt.getStmt(i)) {
        blockStmt.removeStmt(i);
        return;
      }
    }
    throw new FailedReductionException("Should be unreachable.");
  }

  @Override
  public boolean preconditionHolds() {
    if (!blockStmt.getStmts().contains(childOfBlockStmt)) {
      // Some other reduction opportunity must have removed the statement already
      return false;
    }
    if (blockStmt.getStmt(blockStmt.getNumStmts() - 1) == childOfBlockStmt
        && blockStmt.getNumStmts() > 1
        && blockStmt.getStmt(blockStmt.getNumStmts() - 2) instanceof CaseLabel) {
      // Removing this statement would leave an empty final switch case, which we don't want to do.
      return false;
    }
    if (parentOfBlockStmt instanceof SwitchStmt
        && childOfBlockStmt instanceof CaseLabel
        && blockStmt.getStmt(0) == childOfBlockStmt
        && blockStmt.getNumStmts() > 1
        && !(blockStmt.getStmt(1) instanceof CaseLabel)) {
      // Removing this statement would lead to a switch statement with a non-label as its first
      // inner statement, which we don't want to do.
      return false;
    }

    return true;
  }
}
