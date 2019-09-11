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
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.ArrayList;
import java.util.List;

public class FlattenForLoopReductionOpportunity extends FlattenLoopReductionOpportunity {

  public FlattenForLoopReductionOpportunity(IAstNode parent, ForStmt forStmt,
                                            VisitationDepth depth) {
    super(parent, forStmt, depth);
  }

  @Override
  void applyReductionImpl() {
    final List<Stmt> newStmts = new ArrayList<>();
    final ForStmt forStmt = (ForStmt) getLoopStmt();
    newStmts.add(forStmt.getInit());
    if (forStmt.hasCondition()) {
      newStmts.add(new ExprStmt(forStmt.getCondition()));
    }
    addLoopBody(newStmts);
    if (forStmt.hasIncrement()) {
      newStmts.add(new ExprStmt(forStmt.getIncrement()));
    }
    doReplacement(newStmts);
  }

}
