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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.SideEffectChecker;
import com.graphicsfuzz.common.util.StructUtils;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.List;

public class StmtReductionOpportunities
      extends ReductionOpportunitiesBase<StmtReductionOpportunity> {

  private final TranslationUnit tu;

  private StmtReductionOpportunities(TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.tu = tu;
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
      addOpportunity(new StmtReductionOpportunity(block, child, getVistitationDepth()));
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

    if (SideEffectChecker.isSideEffectFree(stmt, context.getShadingLanguageVersion())) {
      return true;
    }

    if (injectionTracker.enclosedByDeadCodeInjection()) {
      return true;
    }

    if (injectionTracker.underUnreachableSwitchCase() && !isZeroSwitchCase(stmt)) {
      return true;
    }

    if (isLoopLimiterBlock(stmt)) {
      return true;
    }

    // We cannot remove non-void return statements without special care, unless we are inside an
    // injected block
    if (containsNonVoidReturn(stmt)) {
      if (!isReturnFollowedBySubsequentReturn(block, childIndex)) {
        return false;
      }
    }

    return context.reduceEverywhere()
          || (isLiveCodeInjection(stmt) && !referencesLoopLimiter(stmt))
          || enclosingFunctionIsDead();

  }

  /**
   * Returns true if and only if the statement at childIndex is a return statement, and the block
   * has another return statement at a later index.
   */
  private boolean isReturnFollowedBySubsequentReturn(BlockStmt block, int childIndex) {
    if (!(block.getStmt(childIndex) instanceof ReturnStmt)) {
      return false;
    }
    for (int subsequentChildIndex = childIndex + 1; subsequentChildIndex < block.getNumStmts();
         subsequentChildIndex++) {
      if (block.getStmt(subsequentChildIndex) instanceof ReturnStmt) {
        return true;
      }
    }
    return false;
  }

  private boolean isLoopLimiterBlock(Stmt stmt) {
    // Identifies when a block starts with a loop-limiter declaration, in which case the whole
    // block can go.  We are really careful about otherwise removing loop-limiters, so this is
    // the chance to do it!
    if (!(stmt instanceof BlockStmt)) {
      return false;
    }
    final BlockStmt blockStmt = (BlockStmt) stmt;
    if (blockStmt.getNumStmts() == 0) {
      return false;
    }
    final Stmt firstStmt = blockStmt.getStmt(0);
    if (!(firstStmt instanceof DeclarationStmt)) {
      return false;
    }
    final DeclarationStmt declarationStmt = (DeclarationStmt) firstStmt;
    if (declarationStmt.getVariablesDeclaration().getNumDecls() == 0) {
      return false;
    }
    return isLooplimiter(declarationStmt.getVariablesDeclaration().getDeclInfo(0).getName());
  }

  static boolean referencesLoopLimiter(Stmt stmt) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final String name = variableIdentifierExpr.getName();
        if (isLooplimiter(name)) {
          predicateHolds();
        }
      }
    }.test(stmt);
  }

  private boolean isZeroSwitchCase(Stmt stmt) {
    return stmt instanceof ExprCaseLabel
          && ((IntConstantExpr) ((ExprCaseLabel) stmt).getExpr()).getValue().equals("0");
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

  private static boolean isLiveInjectedVariableName(String name) {
    return name.startsWith(Constants.LIVE_PREFIX);
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

  private boolean containsNonVoidReturn(Stmt stmt) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        if (returnStmt.hasExpr()) {
          predicateHolds();
        }
      }
    }.test(stmt);
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
          && name.contains("looplimiter");
  }

  private boolean isEmptyAndUnreferencedDeclaration(DeclarationStmt stmt) {
    if (stmt.getVariablesDeclaration().getNumDecls() != 0) {
      return false;
    }

    return !StructUtils.declaresReferencedStruct(tu,
        stmt.getVariablesDeclaration());
  }

}
