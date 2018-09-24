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
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public final class StmtReductionOpportunity extends AbstractReductionOpportunity {

  private BlockStmt blockStmt;
  private Stmt child;

  public StmtReductionOpportunity(BlockStmt blockStmt, Stmt child,
      VisitationDepth depth) {
    super(depth);
    this.blockStmt = blockStmt;
    this.child = child;
  }

  public Stmt getChild() {
    return child;
  }

  @Override
  public void applyReductionImpl() {
    for (int i = 0; i < blockStmt.getNumStmts(); i++) {
      if (child == blockStmt.getStmt(i)) {
        blockStmt.removeStmt(i);
        return;
      }
    }
    throw new FailedReductionException("Should be unreachable.");
  }

  @Override
  public boolean preconditionHolds() {
    if (!blockStmt.getStmts().contains(child)) {
      // Some other reduction opportunity must have removed the statement already
      return false;
    }
    return true;
  }
}
