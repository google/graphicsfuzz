/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class LoopLimiterImpactChecker {

  // Loops arising from live-injected code maybe equipped with *limiters*, which bound the number
  // of times they can iterate.  This field records those loops whose removal from the shader will
  // not impact the limiting of remaining loops.
  //
  // For example, consider the following:
  //
  //     GLF_live_loop_limiter1 = 0;
  // (1) while(true) {
  //       if (GLF_live_loop_limiter1 >= 4) {
  // (2)     for (int i = 0; i < 10; i++) {
  //           // nop
  //         }
  //         break;
  //       }
  // (3)   for(i = 0; i < 1; i++) {
  //         live_loop_limiter1++;
  //       }
  //       GLF_live_loop_limiter2 = 0;
  // (4)   while(true) {
  //         if (GLF_live_loop_limiter2 >= 3) {
  //           break;
  //         }
  //         GLF_live_loop_limiter2++;
  //       }
  //     }
  //
  // Loop (1) can be removed without impacting the limiting of remaining loops: the only loop
  // limiter to which it refers that is declared outside (1) is 'live_loop_limiter1', and (1) is
  // outer-most loop in which 'live_loop_limiter1' is visible.
  //
  // Loop (2) can be removed without impacting the limiting of remaining loops because it does
  // not reference any loop limiter variables.
  //
  // Loop (3) *cannot* be removed without impacting the limiting of remaining loops because it
  // references 'live_loop_limiter1', yet is not one of the outer-most loops in which
  // 'live_loop_limiter1' is visible (loop (1) is).
  //
  // Loop (4) can be removed without impacting the limiting of remaining loops: the only loop
  // limiter to which it refers that is declared outside (4) is 'live_loop_limiter2', and (4) is
  // the outer-most loop in which 'live_loop_limiter1' is visible.
  //
  // The presence of a loop in this set does not necessarily mean that the loop can be removed;
  // other conditions may need to be met for that to be the case.  It just means that if the loop
  // were to be removed, the limiting of remaining loops would not be affected.
  private final Set<LoopStmt> loopsThatCanBeRemovedWithoutImpactingLoopLimiting;

  // This captures loop limiters whose associated loops have been removed, and allows removal of
  // left-over statements that reference such loop limiters.
  private final Set<VariablesDeclaration> nonRedundantLoopLimiters;

  public LoopLimiterImpactChecker(TranslationUnit tu) {

    this.loopsThatCanBeRemovedWithoutImpactingLoopLimiting = new HashSet<>();
    this.nonRedundantLoopLimiters = new HashSet<>();

    new InjectionTrackingVisitor(tu) {

      // A stack that keeps track of the nest of loops currently being visited and, for each loop,
      // the loop limiters declared in the body of that loop but not in a deeper loop.  The first
      // element in each pair must be a loop statement, with the exception of the very first pair
      // whose first element is a function's block statement (to capture those loop limiters not
      // declared in any loop).
      private final List<ImmutablePair<Stmt, Set<VariablesDeclaration>>> loopStack =
          new ArrayList<>();

      @Override
      public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
        assert loopStack.isEmpty();
        loopStack.add(new ImmutablePair<>(functionDefinition.getBody(), new HashSet<>()));
        super.visitFunctionDefinition(functionDefinition);
        assert loopStack.size() == 1;
        loopStack.remove(0);
      }

      @Override
      public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
        super.visitVariablesDeclaration(variablesDeclaration);

        if (getEnclosingFunction() == null) {
          // Loop limiters are not relevant at global scope.
          return;
        }

        // We are in a function, so should have a non-empty loop stack.
        assert !loopStack.isEmpty();

        // Is this a loop limiter?  By construction a loop limiter is declared on its own and has a
        // special name.
        if (variablesDeclaration.getNumDecls() == 1
            && Constants.isLooplimiterVariableName(variablesDeclaration.getDeclInfo(0).getName())) {
          // It is a loop limiter, so add the declaration to the set of declarations at the top of
          // the stack.
          loopStack.get(loopStack.size() - 1).getRight().add(variablesDeclaration);
        }
      }

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);

        // We are only interested in references to loop limiters.
        if (!Constants.isLooplimiterVariableName(variableIdentifierExpr.getName())) {
          return;
        }

        // We are not interested in loop limiter references that occur under a fuzzed macro,
        // because such uses are guaranteed not to affect loop limiting.
        if (injectionTracker.underFuzzedMacro()) {
          return;
        }

        // Get the variables declaration associated with the loop limiter reference, which by
        // construction must be in scope.
        final VariablesDeclaration variablesDeclaration =
            getCurrentScope().lookupScopeEntry(variableIdentifierExpr.getName())
                .getVariablesDeclaration();

        // Walk the loop stack backwards until we find the loop in which this limiter was declared
        // (or until we reach the bottom of the stack, in the case that the limiter is declared
        // outside any loop).
        for (int i = loopStack.size() - 1; i >= 0; i--) {
          if (i < loopStack.size() - 1) {
            // The loop limiter variable is declared in some loop body that is shallower than the
            // loop body in which it is used.  It thus may not be redundant: it might be being used
            // to limit that loop.
            nonRedundantLoopLimiters.add(variablesDeclaration);
          }
          if (loopStack.get(i).getRight().contains(variablesDeclaration)) {
            // This is where the loop limiter is declared; shallower loops are not affected by this
            // loop limiter.
            break;
          }
          if (i < loopStack.size() - 1) {
            // We have not yet found the loop limiter declaration, so removal of a deeper loop
            // might affect the limiting of remaining loops.
            final LoopStmt loopStmt = (LoopStmt) loopStack.get(i + 1).getLeft();
            loopsThatCanBeRemovedWithoutImpactingLoopLimiting.remove(loopStmt);
          }
        }
      }

      @Override
      public void visitDoStmt(DoStmt doStmt) {
        beforeLoop(doStmt);
        super.visitDoStmt(doStmt);
        afterLoop();
      }

      @Override
      public void visitForStmt(ForStmt forStmt) {
        beforeLoop(forStmt);
        super.visitForStmt(forStmt);
        afterLoop();
      }

      @Override
      public void visitWhileStmt(WhileStmt whileStmt) {
        beforeLoop(whileStmt);
        super.visitWhileStmt(whileStmt);
        afterLoop();
      }

      private void beforeLoop(LoopStmt loopStmt) {
        loopsThatCanBeRemovedWithoutImpactingLoopLimiting.add(loopStmt);
        loopStack.add(new ImmutablePair<>(loopStmt,
            new HashSet<>()));
      }

      private void afterLoop() {
        loopStack.remove(loopStack.size() - 1);
      }

    }.visit(tu);
  }

  /**
   * Determines whether the given loop may impact the limiting of other loops.
   * @param loopStmt A loop statement to be checked.
   * @return true if and only if the given loop does not impact the limiting of other loops.
   */
  public boolean doesNotImpactLoopLimiting(LoopStmt loopStmt) {
    return loopsThatCanBeRemovedWithoutImpactingLoopLimiting.contains(loopStmt);
  }

  /**
   * Determines whether a given node references a non-redundant loop limiter declared in its
   * enclosing scope.
   * @param node An AST node to be checked.
   * @param scope Variables in the scope of the node.
   * @return True if and only if the node references some non-redundant loop limiter that is
   *         declared in the given scope.
   */
  public boolean referencesNonRedundantLoopLimiter(IAstNode node, Scope scope) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final String name = variableIdentifierExpr.getName();
        if (!Constants.isLooplimiterVariableName(name)) {
          // We are only interested in loop limiters.
          return;
        }
        // This loop limiter might be declared inside 'stmt', which is OK; we're only interested
        // in it if it is part of 'scope'.
        final ScopeEntry scopeEntry = scope.lookupScopeEntry(name);
        if (scopeEntry == null) {
          // The loop limiter was not part of 'scope'.
          return;
        }
        // Finally, check whether the loop limiter is redundant; we only care about non-redundant
        // loop limiters.
        if (nonRedundantLoopLimiters.contains(scopeEntry.getVariablesDeclaration())) {
          predicateHolds();
        }
      }
    }.test(node);
  }

}
