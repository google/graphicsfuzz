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
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.HashSet;
import java.util.Set;

class NotReferencedFromLiveContext extends StandardVisitor {

  private final Set<String> functionsCalledFromPotentiallyLiveContext;
  private final InjectionTracker injectionTracker;

  NotReferencedFromLiveContext(IAstNode node) {
    this.functionsCalledFromPotentiallyLiveContext = new HashSet<>();
    this.injectionTracker = new InjectionTracker();
    visit(node);
  }

  boolean neverCalledFromLiveContext(String functionName) {
    if (functionName.equals("main")) {
      return false;
    }
    return !functionsCalledFromPotentiallyLiveContext.contains(functionName);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    if (inPotentiallyLiveContext()) {
      functionsCalledFromPotentiallyLiveContext.add(functionCallExpr.getCallee());
    }
    if (MacroNames.isFuzzed(functionCallExpr)) {
      injectionTracker.enterFuzzedMacro();
    }
    super.visitFunctionCallExpr(functionCallExpr);
    if (MacroNames.isFuzzed(functionCallExpr)) {
      injectionTracker.exitFuzzedMacro();
    }
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    for (Stmt child : stmt.getStmts()) {
      if (ReductionOpportunitiesBase.isDeadCodeInjection(child)) {
        injectionTracker.enterDeadCodeInjection();
      }
      visit(child);
      if (ReductionOpportunitiesBase.isDeadCodeInjection(child)) {
        injectionTracker.exitDeadCodeInjection();
      }
    }
  }

  private boolean inPotentiallyLiveContext() {
    return !injectionTracker.underFuzzedMacro() && !injectionTracker.enclosedByDeadCodeInjection();
  }

}
