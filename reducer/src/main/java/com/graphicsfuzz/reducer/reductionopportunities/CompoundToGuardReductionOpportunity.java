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
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class CompoundToGuardReductionOpportunity extends AbstractReductionOpportunity {

  // The parent of the compound statement being replaced.
  private final IAstNode parent;

  // The compound statement being replaced.
  private final Stmt compoundStmt;

  // The guard of the compound statement.
  private final Expr guard;

  public CompoundToGuardReductionOpportunity(IAstNode parent, Stmt compoundStmt, Expr guard,
                                             VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.compoundStmt = compoundStmt;
    this.guard = guard;
  }

  @Override
  void applyReductionImpl() {
    parent.replaceChild(compoundStmt, new ExprStmt(guard));
  }

  @Override
  public boolean preconditionHolds() {
    return parent.hasChild(compoundStmt)
        && compoundStmt.hasChild(guard);
  }
}
