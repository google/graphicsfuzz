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
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
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
  protected void visitChildOfBlock(BlockStmt block, Stmt child) {
    if (isEmptyBlockStmt(child) || isDeadCodeInjection(child)
          || allowedToReduceStmt(child)) {
      addOpportunity(new StmtReductionOpportunity(block, child, getVistitationDepth()));
    }
  }

  private boolean isEmptyBlockStmt(Stmt stmt) {
    return stmt instanceof BlockStmt && ((BlockStmt) stmt).getNumStmts() == 0;
  }

  private boolean allowedToReduceStmt(Stmt stmt) {

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
      return false;
    }

    return context.reduceEverywhere()
          || (isLiveCodeInjection(stmt) && !referencesLoopLimiter(stmt))
          || enclosingFunctionIsDead();

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
   * Determines whether the given statement came from a live code injection.
   *
   * @param stmt A statement to be analysed
   * @return Whether the statement is injected live code or not
   */
  public static boolean isLiveCodeInjection(Stmt stmt) {
    if (isSimpleLiveCodeInjection(stmt)) {
      return true;
    }
    if (stmt instanceof BlockStmt) {
      return ((BlockStmt) stmt).getStmts().stream().anyMatch(
        item -> isLiveCodeVariableDeclaration(item) || isSimpleLiveCodeInjection(item));
    }
    return false;
  }

  /**
   * Determines whether the given statement is the declaration of a live code variable.
   *
   * @param stmt A statement to be analysed
   * @return Whether the statement is the declaration of a live code variable
   */
  public static boolean isLiveCodeVariableDeclaration(Stmt stmt) {
    if (!(stmt instanceof DeclarationStmt)) {
      return false;
    }
    return ((DeclarationStmt) stmt).getVariablesDeclaration().getDeclInfos()
          .stream()
          .anyMatch(item -> isLiveCodeVariableDeclaration(item));
  }

  public static boolean isLiveCodeVariableDeclaration(VariableDeclInfo vdi) {
    return vdi.getName().startsWith(Constants.LIVE_PREFIX);
  }

  /**
   * Determines whether the given statement came from a live code injection.
   *
   * @param stmt A statement to be analysed
   * @return Whether the statement is injected live code or not
   */
  public static boolean isSimpleLiveCodeInjection(Stmt stmt) {
    if (!(stmt instanceof ExprStmt)) {
      return false;
    }
    final Expr expr = ((ExprStmt) stmt).getExpr();
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
      return ((FunctionCallExpr) expr).getCallee().startsWith(Constants.LIVE_PREFIX);
    }
    return false;
  }

  private static boolean isLiveInjectionVariableReference(Expr lhs) {
    while (lhs instanceof MemberLookupExpr) {
      lhs = ((MemberLookupExpr) lhs).getStructure();
    }
    return lhs instanceof VariableIdentifierExpr && ((VariableIdentifierExpr) lhs).getName()
          .startsWith(
                Constants.LIVE_PREFIX);
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
    return name.startsWith(Constants.LIVE_PREFIX)
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
