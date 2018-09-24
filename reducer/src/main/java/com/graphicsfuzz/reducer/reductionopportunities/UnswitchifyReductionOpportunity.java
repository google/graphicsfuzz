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
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.ArrayList;

public class UnswitchifyReductionOpportunity extends AbstractReductionOpportunity {

  private final SwitchStmt switchStmt;
  private final IAstNode parent;

  public UnswitchifyReductionOpportunity(SwitchStmt switchStmt, IAstNode parent,
      VisitationDepth visitationDepth) {
    super(visitationDepth);
    this.switchStmt = switchStmt;
    this.parent = parent;
  }

  @Override
  public void applyReductionImpl() {
    BlockStmt replacement = new BlockStmt(new ArrayList<>(), true);

    boolean reachedOriginalCode = false;
    for (Stmt stmt : switchStmt.getBody().getStmts()) {
      if (!reachedOriginalCode) {
        if (!(stmt instanceof ExprCaseLabel && isZeroLabel((ExprCaseLabel) stmt))) {
          continue;
        }
        reachedOriginalCode = true;
      }
      if (stmt instanceof BreakStmt) {
        break;
      }
      if (stmt instanceof CaseLabel) {
        continue;
      }
      replacement.addStmt(stmt);
    }
    parent.replaceChild(switchStmt, replacement);
  }

  private boolean isZeroLabel(ExprCaseLabel label) {
    return isZero(label.getExpr());
  }

  private boolean isZero(Expr expr) {
    return expr instanceof IntConstantExpr
        && ((IntConstantExpr) expr).getText().equals("0");
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }
}
