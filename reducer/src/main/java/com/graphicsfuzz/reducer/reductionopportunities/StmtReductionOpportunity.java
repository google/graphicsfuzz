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
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.util.StatsVisitor;

public final class StmtReductionOpportunity extends AbstractReductionOpportunity {

  private final TranslationUnit translationUnit;
  private final FunctionDefinition enclosingFunction;
  private final BlockStmt blockStmt;
  private final Stmt childOfBlockStmt;

  // This tracks the number of nodes that will be removed by applying the opportunity at its
  // time of creation (this number may be different when the opportunity is actually applied,
  // due to the effects of other opportunities).
  private final int numRemovableNodes;

  /**
   * Creates an opportunity to remove childOfBlockStmt from blockStmt.
   * @param translationUnit The translation unit being reduced
   * @param enclosingFunction A function that encloses the statement to be removed
   * @param blockStmt A block that directly encloses the statement to be removed
   * @param childOfBlockStmt The statement to be removed
   * @param depth The depth at which this opportunity was found
   */
  public StmtReductionOpportunity(TranslationUnit translationUnit,
                                  FunctionDefinition enclosingFunction,
                                  BlockStmt blockStmt,
                                  Stmt childOfBlockStmt,
                                  VisitationDepth depth) {
    super(depth);
    this.translationUnit = translationUnit;
    this.enclosingFunction = enclosingFunction;
    this.blockStmt = blockStmt;
    this.childOfBlockStmt = childOfBlockStmt;
    this.numRemovableNodes = new StatsVisitor(childOfBlockStmt).getNumNodes();
  }

  @Override
  public void applyReductionImpl() {
    for (int i = 0; i < blockStmt.getNumStmts(); i++) {
      if (childOfBlockStmt == blockStmt.getStmt(i)) {
        blockStmt.removeStmt(i);
        return;
      }
    }
    throw new FailedReductionException("Should be unreachable.");
  }

  @Override
  public boolean preconditionHolds() {
    if (!blockStmt.getStmts().contains(childOfBlockStmt)) {
      // Some other reduction opportunity must have removed the statement already
      return false;
    }

    if (blockStmt.getNumStmts() > 1
        && blockStmt.getStmt(blockStmt.getNumStmts() - 1) == childOfBlockStmt
        && blockStmt.getStmt(blockStmt.getNumStmts() - 2) instanceof CaseLabel) {
      // We do not want to remove childOfBlockStmt in the following scenario, as it would lead to an
      // empty final switch case, which we want to avoid:
      //
      // switch(...)
      // { <-------------- blockStmt
      //   ...
      //   case ...:
      //   stmt; <-------- childOfBlockStmt
      // }
      return false;
    }
    if (blockStmt.getNumStmts() > 1
        && childOfBlockStmt instanceof CaseLabel
        && blockStmt.getStmt(0) == childOfBlockStmt
        && !(blockStmt.getStmt(1) instanceof CaseLabel)) {
      // We do not want to remove childOfBlockStmt in the following scenario, as it would lead to a
      // switch body that does not start with a case label:
      //
      // switch(...)
      // { <-------------- blockStmt
      //   case ...: <-------- childOfBlockStmt
      //   notCaseLabel;
      //   ...
      // }
      return false;
    }

    if (removalCouldLeadToLackOfReturnFromNonVoidFunction(translationUnit, enclosingFunction,
        blockStmt,
        childOfBlockStmt)) {
      return false;
    }

    return true;
  }

  public int getNumRemovableNodes() {
    return numRemovableNodes;
  }

  static boolean removalCouldLeadToLackOfReturnFromNonVoidFunction(
      TranslationUnit translationUnit,
      FunctionDefinition enclosingFunction, BlockStmt block, Stmt childOfBlockStmt) {
    if (!containsReachableNonVoidReturn(translationUnit, enclosingFunction, childOfBlockStmt)) {
      return false;
    }
    if (statementIsPrecededByReturn(block, childOfBlockStmt)) {
      return false;
    }
    if (functionEndsWithReturn(enclosingFunction) && !isLastStatementInFunction(enclosingFunction,
        childOfBlockStmt)) {
      return false;
    }
    return true;
  }

  private static boolean containsReachableNonVoidReturn(
      TranslationUnit translationUnit,
      FunctionDefinition enclosingFunction,
      Stmt stmt) {
    return new InjectionTrackingVisitor(translationUnit) {

      private Stmt stmtOfInterest;
      private boolean inStmtOfInterest = false;
      private boolean found = false;

      @Override
      public void visit(IAstNode node) {
        if (node == stmtOfInterest) {
          assert !inStmtOfInterest;
          inStmtOfInterest = true;
        }
        super.visit(node);
        if (node == stmtOfInterest) {
          assert inStmtOfInterest;
          inStmtOfInterest = false;
        }
      }

      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        // We have found a relevant non-void return statement if we are inside the statement of
        // interest, not in dead code, and if this return statement has an associated expression.
        if (inStmtOfInterest && returnStmt.hasExpr()) {
          if (!(injectionTracker.enclosedByDeadCodeInjection()
              || injectionTracker.underUnreachableSwitchCase())) {
            // Note that we do not ask whether the current program point is dead, as that
            // includes being in a dead or statically unreachable function, and we don't want to
            // make the shader invalid by removing a return arbitrarily from one of those.
            found = true;
          }
        }
      }

      private boolean test(FunctionDefinition enclosingFunction, Stmt stmtOfInterest) {
        this.stmtOfInterest = stmtOfInterest;
        visit(enclosingFunction);
        return found;
      }

    }.test(enclosingFunction, stmt);

  }

  private static boolean statementIsPrecededByReturn(BlockStmt block, Stmt childOfBlockStmt) {
    for (Stmt stmt : block.getStmts()) {
      if (stmt == childOfBlockStmt) {
        return false;
      }
      if (stmt instanceof ReturnStmt) {
        return true;
      }
    }
    throw new RuntimeException("Should be unreachable.");
  }

  private static boolean functionEndsWithReturn(FunctionDefinition functionDefinition) {
    return functionDefinition.getBody().getNumStmts() > 0
        && functionDefinition.getBody().getLastStmt()
        instanceof ReturnStmt;
  }

  private static boolean isLastStatementInFunction(FunctionDefinition functionDefinition,
                                                   Stmt stmt) {
    return functionDefinition.getBody().getNumStmts() > 0
        && functionDefinition.getBody().getLastStmt() == stmt;
  }

}
