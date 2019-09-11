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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.List;

public abstract class FlattenLoopReductionOpportunity extends AbstractReductionOpportunity {

  private final IAstNode parent;
  private final LoopStmt loopStmt;

  FlattenLoopReductionOpportunity(IAstNode parent, LoopStmt loopStmt,
                                            VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.loopStmt = loopStmt;
  }

  LoopStmt getLoopStmt() {
    return loopStmt;
  }

  void doReplacement(List<Stmt> newStmts) {
    parent.replaceChild(loopStmt, new BlockStmt(newStmts, true));
  }

  void addLoopBody(List<Stmt> newStmts) {
    if (loopStmt.getBody() instanceof BlockStmt) {
      newStmts.addAll(((BlockStmt) loopStmt.getBody()).getStmts());
    } else {
      newStmts.add(loopStmt.getBody());
    }
  }

  @Override
  public final boolean preconditionHolds() {
    return parent.hasChild(loopStmt);
  }
}
