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
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.Collections;

/**
 * Turns a switch statement into a do...while(false) loop.  The body of the loop comprises all
 * the non-case/default statements from the switch.  The reason a loop is used is to allow for
 * break statements.  The reduction opportunity is not semantics-preserving.
 */
public class SwitchToLoopReductionOpportunity extends AbstractReductionOpportunity {

  // The parent of the switch statement
  private final IAstNode parent;

  // The switch statement to be replaced
  private final SwitchStmt switchStmt;

  SwitchToLoopReductionOpportunity(VisitationDepth depth, IAstNode parent, SwitchStmt switchStmt) {
    super(depth);
    this.parent = parent;
    this.switchStmt = switchStmt;
  }

  @Override
  void applyReductionImpl() {
    final BlockStmt loopBody = new BlockStmt(Collections.emptyList(), true);
    loopBody.addStmt(new ExprStmt(switchStmt.getExpr()));
    switchStmt.getBody().getStmts().stream().filter(item -> !(item instanceof CaseLabel))
            .forEach(loopBody::addStmt);
    parent.replaceChild(switchStmt, new DoStmt(loopBody, new BoolConstantExpr(false)));
  }

  @Override
  public boolean preconditionHolds() {
    return parent.hasChild(switchStmt);
  }

}
