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

import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.util.StatsVisitor;

public final class StmtReductionOpportunity extends AbstractReductionOpportunity {

  private final BlockStmt blockStmt;
  private final Stmt childOfBlockStmt;

  // This tracks the number of nodes that will be removed by applying the opportunity at its
  // time of creation (this number may be different when the opportunity is actually applied,
  // due to the effects of other opportunities).
  private final int numRemovableNodes;

  public StmtReductionOpportunity(BlockStmt blockStmt,
                                  Stmt childOfBlockStmt,
                                  VisitationDepth depth) {
    super(depth);
    this.blockStmt = blockStmt;
    this.childOfBlockStmt = childOfBlockStmt;
    this.numRemovableNodes = new StatsVisitor(childOfBlockStmt).getNumNodes();
  }

  public Stmt getChild() {
    return childOfBlockStmt;
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

    if (blockStmt.getNumStmts() > 1
        && blockStmt.getStmt(blockStmt.getNumStmts() - 1) == childOfBlockStmt
        && blockStmt.getStmt(blockStmt.getNumStmts() - 2) instanceof CaseLabel) {
      // We do not want to remove childOfBlockStmt in the following scenario, as it would lead to an
      // empty final switch case, which we want to avoid:
      //
      // switch(...)
      // { <-------------- blockStmt
      //   ...
      //   case ...:
      //   stmt; <-------- childOfBlockStmt
      // }
      return false;
    }
    if (blockStmt.getNumStmts() > 1
        && childOfBlockStmt instanceof CaseLabel
        && blockStmt.getStmt(0) == childOfBlockStmt
        && !(blockStmt.getStmt(1) instanceof CaseLabel)) {
      // We do not want to remove childOfBlockStmt in the following scenario, as it would lead to a
      // switch body that does not start with a case label:
      //
      // switch(...)
      // { <-------------- blockStmt
      //   case ...: <-------- childOfBlockStmt
      //   notCaseLabel;
      //   ...
      // }
      return false;
    }

    return true;
  }

  public int getNumRemovableNodes() {
    return numRemovableNodes;
  }

}
