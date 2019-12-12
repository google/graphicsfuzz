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
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.ArrayList;
import java.util.List;

public class FlattenDoWhileLoopReductionOpportunity extends FlattenLoopReductionOpportunity {

  public FlattenDoWhileLoopReductionOpportunity(IAstNode parent, DoStmt doStmt,
                                                VisitationDepth depth) {
    super(parent, doStmt, depth);
  }

  @Override
  void applyReductionImpl() {
    final List<Stmt> newStmts = new ArrayList<>();
    addLoopBody(newStmts);
    newStmts.add(new ExprStmt(getLoopStmt().getCondition()));
    doReplacement(newStmts);
  }

}
