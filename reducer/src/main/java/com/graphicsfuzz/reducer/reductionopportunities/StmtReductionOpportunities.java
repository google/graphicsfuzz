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
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.MacroNames;
import com.graphicsfuzz.common.util.SideEffectChecker;
import com.graphicsfuzz.common.util.StructUtils;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.List;

public class StmtReductionOpportunities
      extends ReductionOpportunitiesBase<StmtReductionOpportunity> {

  // The translation unit in which statement reduction opportunities are being sought.
  private final TranslationUnit tu;

  // Used to identify when code that references loop limiters can be safely removed.
  private final LoopLimiterImpactChecker loopLimiterImpactChecker;

  private StmtReductionOpportunities(TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.tu = tu;
    this.loopLimiterImpactChecker = new LoopLimiterImpactChecker(tu);

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
    if (!loopLimiterImpactChecker.referencesNonRedundantLoopLimiter(stmt, getCurrentScope())) {
      return true;
    }

    // We have a live code statement that does reference a loop limiter.  We can remove it if it
    // is a loop such that we know removing this loop will not impact on the limiting of other
    // loops.
    return stmt instanceof LoopStmt
        && loopLimiterImpactChecker.doesNotImpactLoopLimiting((LoopStmt) stmt);

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
          .anyMatch(item -> Constants.isLiveInjectedVariableName(item.getName()));
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
      return Constants.isLiveInjectedVariableName(((FunctionCallExpr) expr).getCallee());
    }
    return false;
  }

  private static boolean isLiveInjectionVariableReference(Expr lhs) {
    while (lhs instanceof MemberLookupExpr || lhs instanceof ArrayIndexExpr) {
      if (lhs instanceof MemberLookupExpr) {
        lhs = ((MemberLookupExpr) lhs).getStructure();
      } else {
        lhs = ((ArrayIndexExpr) lhs).getArray();
      }
    }
    if (!(lhs instanceof VariableIdentifierExpr)) {
      return false;
    }
    return Constants.isLiveInjectedVariableName(((VariableIdentifierExpr) lhs).getName());
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

  private boolean isEmptyAndUnreferencedDeclaration(DeclarationStmt stmt) {
    if (stmt.getVariablesDeclaration().getNumDecls() != 0) {
      return false;
    }

    return !StructUtils.declaresReferencedStruct(tu,
        stmt.getVariablesDeclaration());
  }


}
