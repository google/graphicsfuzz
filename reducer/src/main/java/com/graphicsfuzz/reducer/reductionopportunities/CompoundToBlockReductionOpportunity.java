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
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.ArrayList;
import java.util.List;

public class CompoundToBlockReductionOpportunity extends AbstractReductionOpportunity {

  private final IAstNode parent;
  private final Stmt compoundStmt;
  private final Stmt childStmt;

  public CompoundToBlockReductionOpportunity(IAstNode parent, Stmt compoundStmt, Stmt childStmt,
        VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.compoundStmt = compoundStmt;
    this.childStmt = childStmt;
  }

  @Override
  public void applyReductionImpl() {
    Stmt replacement;
    if (compoundStmt instanceof ForStmt) {
      final ForStmt forStmt = (ForStmt) compoundStmt;
      assert childStmt == forStmt.getBody();
      List<Stmt> stmts = new ArrayList<>();
      stmts.add(forStmt.getInit());
      if (forStmt.getBody() instanceof BlockStmt) {
        stmts.addAll(((BlockStmt) forStmt.getBody()).getStmts());
      } else {
        stmts.add(forStmt.getBody());
      }
      replacement = new BlockStmt(stmts, true);
    } else {
      replacement = childStmt;
    }
    parent.replaceChild(compoundStmt, replacement);
  }

  @Override
  public boolean preconditionHolds() {
    return parent.hasChild(compoundStmt)
          && compoundStmt.hasChild(childStmt);
  }

}
