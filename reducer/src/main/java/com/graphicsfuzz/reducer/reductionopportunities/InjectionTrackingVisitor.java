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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.util.MacroNames;
import com.graphicsfuzz.util.Constants;

/**
 * A visitor that tracks information about code injections, to aid in identifying reduction
 * opportunities related to those injections.
 */
public class InjectionTrackingVisitor extends ScopeTrackingVisitor {

  // Used to track e.g. whether traversal is under a dead block or fuzzed macro.
  final InjectionTracker injectionTracker;

  // Provides parent information for the AST.
  final IParentMap parentMap;

  // Records whether functions are referenced from possibly reachable code.
  private final NotReferencedFromLiveContext notReferencedFromLiveContext;

  public InjectionTrackingVisitor(TranslationUnit tu) {
    this.injectionTracker = new InjectionTracker();
    this.parentMap = IParentMap.createParentMap(tu);
    this.notReferencedFromLiveContext = new NotReferencedFromLiveContext(tu);
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    injectionTracker.enterSwitch(MacroNames.isSwitch(switchStmt.getExpr()));
    super.visitSwitchStmt(switchStmt);
    injectionTracker.leaveSwitch(MacroNames.isSwitch(switchStmt.getExpr()));
  }

  @Override
  public void visitBlockStmt(BlockStmt block) {
    enterBlockStmt(block);

    for (int i = 0; i < block.getNumStmts(); i++) {

      final Stmt child = block.getStmt(i);

      if (child instanceof ExprCaseLabel) {
        // It is important to notify the injection tracker of a switch case *before* we visit the
        // switch case so that we can identify when we have entered the reachable part of an
        // injected switch statement.
        injectionTracker.notifySwitchCase((ExprCaseLabel) child);
      }

      visitChildOfBlock(block, i);

      if (child instanceof BreakStmt && parentMap.getParent(block) instanceof SwitchStmt) {
        injectionTracker.notifySwitchBreak();
      }

      visit(child);

    }
    leaveBlockStmt(block);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    final boolean isFuzzedMacro = MacroNames.isFuzzed(functionCallExpr);
    if (isFuzzedMacro) {
      injectionTracker.enterFuzzedMacro();
    }
    super.visitFunctionCallExpr(functionCallExpr);
    if (isFuzzedMacro) {
      injectionTracker.exitFuzzedMacro();
    }
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    // This method is overridden in order to allow tracking of when we are inside a dead code
    // injection.

    // Even for an "if(_GLF_DEAD(...))", the condition itself is not inside the dead code injection,
    // so we visit it as usual first.
    visitChildFromParent(ifStmt.getCondition(), ifStmt);

    if (isDeadCodeInjection(ifStmt)) {
      // This is a dead code injection, so update the injection tracker appropriately before
      // visiting the 'then' and (possibly) 'else' branches.
      injectionTracker.enterDeadCodeInjection();
    }
    visit(ifStmt.getThenStmt());
    if (ifStmt.hasElseStmt()) {
      visit(ifStmt.getElseStmt());
    }
    if (isDeadCodeInjection(ifStmt)) {
      // Update the injection tracker to indicate that we have left this dead code injection.
      injectionTracker.exitDeadCodeInjection();
    }
  }

  void visitChildOfBlock(BlockStmt block, int childIndex) {
    // Override in subclass if needed
  }

  boolean currentProgramPointIsDeadCode() {
    return injectionTracker.enclosedByDeadCodeInjection()
        || injectionTracker.underUnreachableSwitchCase()
        || enclosingFunctionIsDead();
  }

  private boolean enclosingFunctionIsDead() {
    if (atGlobalScope()) {
      return false;
    }
    final String enclosingFunctionName = getEnclosingFunction().getPrototype().getName();
    return enclosingFunctionName.startsWith(Constants.DEAD_PREFIX)
        || notReferencedFromLiveContext.neverCalledFromLiveContext(enclosingFunctionName);
  }

  static boolean isDeadCodeInjection(Stmt stmt) {
    return stmt instanceof IfStmt && MacroNames
        .isDeadByConstruction(((IfStmt) stmt).getCondition());
  }

}
