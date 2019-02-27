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
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public final class IdentityMutationReductionOpportunity extends AbstractReductionOpportunity {

  private IAstNode parent;
  private Expr childToReduce;
  private OpaqueFunctionType function;

  IdentityMutationReductionOpportunity(IAstNode parent, Expr childToReduce,
                                       OpaqueFunctionType function,
                                       VisitationDepth depth) {
    super(depth);
    this.parent = parent;
    this.childToReduce = childToReduce;
    this.function = function;
  }

  private Expr extractExpr(Expr exprToReduce) {
    switch (function) {
      case FALSE:
        assert MacroNames.isFalse(exprToReduce);
        break;
      case IDENTITY:
        assert MacroNames.isIdentity(exprToReduce);
        break;
      case ONE:
        assert MacroNames.isOne(exprToReduce);
        break;
      case TRUE:
        assert MacroNames.isTrue(exprToReduce);
        break;
      case ZERO:
        assert MacroNames.isZero(exprToReduce);
        break;
      default:
        throw new FailedReductionException("Unknown mutation");
    }
    return ((FunctionCallExpr) exprToReduce).getArg(0);
  }

  @Override
  public void applyReductionImpl() {
    parent.replaceChild(childToReduce, getReducedExpr());
  }

  private Expr getReducedExpr() {
    return extractExpr(childToReduce);
  }

  @Override
  public String toString() {
    return "IdentityMutationReductionOpportunity(" + childToReduce.getText()
        + " -> " + getReducedExpr().getText() + ")";
  }

  @Override
  public boolean preconditionHolds() {
    if (!parent.hasChild(childToReduce)) {
      return false;
    }
    return true;
  }

}
