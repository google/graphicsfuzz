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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.MacroNames;
import com.graphicsfuzz.common.util.SideEffectChecker;
import com.graphicsfuzz.common.util.StructUtils;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class StmtReductionOpportunities
      extends ReductionOpportunitiesBase<StmtReductionOpportunity> {

  // The translation unit in which statement reduction opportunities are being sought.
  private final TranslationUnit tu;

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

  private StmtReductionOpportunities(TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.tu = tu;
    this.loopsThatCanBeRemovedWithoutImpactingLoopLimiting = new HashSet<>();
    new LoopLimiterImpactChecker(tu).visit(tu);

  }

  static List<StmtReductionOpportunity> findOpportunities(ShaderJob shaderJob,
                                                          ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<StmtReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    StmtReductionOpportunities finder =
          new StmtReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  protected void visitChildOfBlock(BlockStmt block, int index) {
    final Stmt child = block.getStmt(index);
    if (isEmptyBlockStmt(child) || isDeadCodeInjection(child)
          || allowedToReduceStmt(block, index)) {
      addOpportunity(new StmtReductionOpportunity(tu, getEnclosingFunction(), block, child,
          getVistitationDepth()));
    }
  }

  private boolean isEmptyBlockStmt(Stmt stmt) {
    return stmt instanceof BlockStmt && ((BlockStmt) stmt).getNumStmts() == 0;
  }

  /**
   * Returns true if and only if it is OK to reduce the statement at index childIndex of block.
   */
  private boolean allowedToReduceStmt(BlockStmt block, int childIndex) {

    final Stmt stmt = block.getStmt(childIndex);

    // We deal separately with removal of declarations
    if (stmt instanceof DeclarationStmt) {
      if (isEmptyAndUnreferencedDeclaration((DeclarationStmt) stmt)) {
        return true;
      }
      return false;
    }

    if (stmt instanceof NullStmt) {
      return true;
    }

    if (isRedundantCopy(stmt)) {
      return true;
    }

    if (SideEffectChecker.isSideEffectFree(stmt, context.getShadingLanguageVersion(), shaderKind)) {
      return true;
    }

    if (currentProgramPointIsDeadCode()) {
      return true;
    }

    // Unless we are in an injected dead code block, we need to be careful about removing
    // non-void return statements, so as to avoid making the shader invalid.
    if (StmtReductionOpportunity
        .removalCouldLeadToLackOfReturnFromNonVoidFunction(tu, getEnclosingFunction(), block,
            stmt)) {
      return false;
    }

    // If we're not preserving semantics, it's fine to remove this statement.
    if (context.reduceEverywhere()) {
      return true;
    }

    // Otherwise, we can't remove the statement unless we are sure it is live code.
    if (!isLiveCodeInjection(stmt)) {
      return false;
    }

    // Then, for live code, we need to take care about removing statements that manipulate loop
    // limiters.  If this statement does not reference any loop limiters, that's fine.
    if (!referencesLoopLimiter(stmt, getCurrentScope())) {
      return true;
    }

    // We have a live code statement that does reference a loop limiter.  We can remove it if it
    // is a loop such that we know removing this loop will not impact on the limiting of other
    // loops.
    return stmt instanceof LoopStmt
        && loopsThatCanBeRemovedWithoutImpactingLoopLimiting.contains(stmt);

  }

  static boolean referencesLoopLimiter(Stmt stmt, Scope scope) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final String name = variableIdentifierExpr.getName();
        if (isLooplimiter(name) && scope.lookupScopeEntry(name) != null) {
          predicateHolds();
        }
      }
    }.test(stmt);
  }

  /**
   * Determines whether the given statement came from a live code injection.  This is the case if
   * one of the following holds:
   * - the statement is an expression statement that can be identified as coming from a live code
   *   injection.
   * - the statement is a loop, conditional or switch whose guard refers
   *   to a live-injected variable directly (i.e., not due to a 'fuzzed' macro).
   *
   * @param stmt A statement to be analysed
   * @return Whether the statement is injected live code or not
   */
  static boolean isLiveCodeInjection(Stmt stmt) {
    if (stmt instanceof ExprStmt) {
      return isSimpleLiveCodeInjection((ExprStmt) stmt);
    }
    if (stmt instanceof IfStmt) {
      return refersDirectlyToLiveInjectedVariable(((IfStmt) stmt).getCondition());
    }
    if (stmt instanceof SwitchStmt) {
      return refersDirectlyToLiveInjectedVariable(((SwitchStmt) stmt).getExpr());
    }

    // Deal with for loops and non-for loops separately.
    if (stmt instanceof ForStmt) {
      final ForStmt forStmt = (ForStmt) stmt;
      if (forStmt.hasIncrement()
          && refersDirectlyToLiveInjectedVariable(forStmt.getIncrement())) {
        return true;
      }
      if (forStmt.hasCondition() && refersDirectlyToLiveInjectedVariable(forStmt.getCondition())) {
        return true;
      }
      return isLiveCodeInjection(forStmt.getInit());
    } else if (stmt instanceof LoopStmt) {
      return refersDirectlyToLiveInjectedVariable(((LoopStmt) stmt).getCondition());
    }
    return false;
  }

  /**
   * Returns true if and only if the expression refers to a live-injected variable that is not
   * under a 'fuzzed' macro.
   */
  private static boolean refersDirectlyToLiveInjectedVariable(Expr expr) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        if (isLiveInjectionVariableReference(variableIdentifierExpr)) {
          predicateHolds();
        }
      }

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        // Do not visit the children of the call if it is a 'fuzzed' macro.
        if (!MacroNames.isFuzzed(functionCallExpr)) {
          super.visitFunctionCallExpr(functionCallExpr);
        }
      }
    }.test(expr);
  }

  /**
   * Determines whether the given statement is the declaration of a live code variable.
   *
   * @param stmt A statement to be analysed
   * @return Whether the statement is the declaration of a live code variable
   */
  static boolean isLiveCodeVariableDeclaration(DeclarationStmt stmt) {
    return stmt.getVariablesDeclaration().getDeclInfos()
          .stream()
          .anyMatch(StmtReductionOpportunities::isLiveCodeVariableDeclaration);
  }

  private static boolean isLiveCodeVariableDeclaration(VariableDeclInfo vdi) {
    final String name = vdi.getName();
    return isLiveInjectedVariableName(name);
  }

  /**
   * <p>
   * Determines whether the given statement came from a live code injection.
   * </p>
   * <p>
   * We identify a statement as being a live code injection if it is either:
   * - an assignment (using e.g. the = or += operators)
   * - a unary increment or decrement (using ++ or --)
   * - a function call
   * </p>
   * @param stmt A statement to be analysed
   * @return Whether the statement is injected live code or not
   */
  static boolean isSimpleLiveCodeInjection(ExprStmt stmt) {
    final Expr expr = stmt.getExpr();
    if (expr instanceof BinaryExpr) {
      if (!((BinaryExpr) expr).getOp().isSideEffecting()) {
        return false;
      }
      return isLiveInjectionVariableReference(((BinaryExpr) expr).getLhs());
    }
    if (expr instanceof UnaryExpr) {
      if (!((UnaryExpr) expr).getOp().isSideEffecting()) {
        return false;
      }
      return isLiveInjectionVariableReference(((UnaryExpr) expr).getExpr());
    }
    if (expr instanceof FunctionCallExpr) {
      return isLiveInjectedVariableName(((FunctionCallExpr) expr).getCallee());
    }
    return false;
  }

  private static boolean isLiveInjectionVariableReference(Expr lhs) {
    while (lhs instanceof MemberLookupExpr) {
      lhs = ((MemberLookupExpr) lhs).getStructure();
    }
    if (!(lhs instanceof VariableIdentifierExpr)) {
      return false;
    }
    return isLiveInjectedVariableName(((VariableIdentifierExpr) lhs).getName());
  }

  private boolean isRedundantCopy(Stmt stmt) {
    if (!(stmt instanceof ExprStmt)) {
      return false;
    }
    final Expr expr = ((ExprStmt) stmt).getExpr();
    if (!(expr instanceof BinaryExpr)) {
      return false;
    }
    final BinaryExpr binaryExpr = (BinaryExpr) expr;
    if (binaryExpr.getOp() != BinOp.ASSIGN) {
      return false;
    }
    final Expr lhs = binaryExpr.getLhs();
    if (!(lhs instanceof VariableIdentifierExpr)) {
      return false;
    }
    final Expr rhs = binaryExpr.getRhs();
    if (!(rhs instanceof VariableIdentifierExpr)) {
      return false;
    }
    return ((VariableIdentifierExpr) lhs).getName()
          .equals(((VariableIdentifierExpr) rhs).getName());
  }

  static boolean isLooplimiter(String name) {
    return isLiveInjectedVariableName(name)
          && name.contains(Constants.LOOP_LIMITER);
  }

  private boolean isEmptyAndUnreferencedDeclaration(DeclarationStmt stmt) {
    if (stmt.getVariablesDeclaration().getNumDecls() != 0) {
      return false;
    }

    return !StructUtils.declaresReferencedStruct(tu,
        stmt.getVariablesDeclaration());
  }

  private class LoopLimiterImpactChecker extends InjectionTrackingVisitor {

    // A stack that keeps track of the nest of loops currently being visited and, for each loop,
    // the loop limiters declared in the body of that loop but not in a deeper loop.
    private final List<ImmutablePair<LoopStmt, Set<VariablesDeclaration>>> loopStack =
        new ArrayList<>();

    private LoopLimiterImpactChecker(TranslationUnit tu) {
      super(tu);
    }

    @Override
    public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
      super.visitVariablesDeclaration(variablesDeclaration);
      // We only keep track of loop limiters declared in some loop.
      if (loopStack.isEmpty()) {
        return;
      }
      // Is this a loop limiter?  By construction a loop limiter is declared on its own and has a
      // special name.
      if (variablesDeclaration.getNumDecls() == 1
          && isLooplimiter(variablesDeclaration.getDeclInfo(0).getName())) {
        // It is a loop limiter, so add the declaration to the set of declarations at the top of
        // the stack.
        loopStack.get(loopStack.size() - 1).getRight().add(variablesDeclaration);
      }
    }

    @Override
    public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
      super.visitVariableIdentifierExpr(variableIdentifierExpr);

      // We are only interested in references to loop limiters.
      if (!isLooplimiter(variableIdentifierExpr.getName())) {
        return;
      }

      // We are only interested in loop limiter references that occur in non-trivial loop nests.
      if (loopStack.size() < 2) {
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
        if (loopStack.get(i).getRight().contains(variablesDeclaration)) {
          // This is where the loop limiter is declared; shallow loops are not affected by this
          // loop limiter.
          break;
        }
        if (i < loopStack.size() - 1) {
          // We have not yet found the loop limiter declaration, so removal of a deeper loop
          // might affect the limiting of remaining loops.
          loopsThatCanBeRemovedWithoutImpactingLoopLimiting.remove(
              loopStack.get(i + 1).getLeft());
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

  }

}
