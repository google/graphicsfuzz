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
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.util.StatsVisitor;

public class SimplifyExprReductionOpportunity extends AbstractReductionOpportunity {

  private final IAstNode parent;
  private final Expr newChild;
  private final Expr originalChild;

  // This tracks the number of nodes that will be removed by applying the opportunity at its
  // time of creation (this number may be different when the opportunity is actually applied,
  // due to the effects of other opportunities).
  private final int numRemovableNodes;

  public SimplifyExprReductionOpportunity(IAstNode parent, Expr newChild, Expr originalChild,
        VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.newChild = newChild;
    this.originalChild = originalChild;
    this.numRemovableNodes =
        new StatsVisitor(originalChild).getNumNodes() - new StatsVisitor(newChild).getNumNodes();
  }

  @Override
  public void applyReductionImpl() {
    parent.replaceChild(originalChild, newChild);
  }

  @Override
  public String toString() {
    return getClass().getName() + ": " + originalChild;
  }

  @Override
  public boolean preconditionHolds() {
    if (!parent.hasChild(originalChild)) {
      return false;
    }
    return true;
  }

  public int getNumRemovableNodes() {
    return numRemovableNodes;
  }

}
