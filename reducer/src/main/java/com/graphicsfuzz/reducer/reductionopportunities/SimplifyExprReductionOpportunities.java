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
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.util.Constants;
import java.util.stream.Collectors;

abstract class SimplifyExprReductionOpportunities
      extends ReductionOpportunitiesBase<SimplifyExprReductionOpportunity> {

  final Typer typer;

  private boolean inLiveInjectedStmtOrDeclaration;

  // Tracks whether we are visiting the components of a loop limiter's declaration (which includes
  // its initializer).  We do not wish to mess with these.
  private boolean inLoopLimiterVariableDeclInfo;

  // Used to assess whether expressions that reference loop limiters can be simplified.
  private final LoopLimiterImpactChecker loopLimiterImpactChecker;

  SimplifyExprReductionOpportunities(
        TranslationUnit tu,
        ReducerContext context) {
    super(tu, context);
    this.typer = new Typer(tu);
    this.inLiveInjectedStmtOrDeclaration = false;
    this.loopLimiterImpactChecker = new LoopLimiterImpactChecker(tu);
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    if (StmtReductionOpportunities.isSimpleLiveCodeInjection(exprStmt)) {
      assert !inLiveInjectedStmtOrDeclaration;
      inLiveInjectedStmtOrDeclaration = true;
    }
    super.visitExprStmt(exprStmt);
    if (StmtReductionOpportunities.isSimpleLiveCodeInjection(exprStmt)) {
      assert inLiveInjectedStmtOrDeclaration;
      inLiveInjectedStmtOrDeclaration = false;
    }
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    if (StmtReductionOpportunities.isLiveCodeVariableDeclaration(declarationStmt)) {
      assert !inLiveInjectedStmtOrDeclaration;
      inLiveInjectedStmtOrDeclaration = true;
    }
    super.visitDeclarationStmt(declarationStmt);
    if (StmtReductionOpportunities.isLiveCodeVariableDeclaration(declarationStmt)) {
      assert inLiveInjectedStmtOrDeclaration;
      inLiveInjectedStmtOrDeclaration = false;
    }
  }

  @Override
  public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
    if (Constants.isLooplimiterVariableName(variableDeclInfo.getName())) {
      assert !inLoopLimiterVariableDeclInfo;
      inLoopLimiterVariableDeclInfo = true;
    }
    super.visitVariableDeclInfo(variableDeclInfo);
    if (Constants.isLooplimiterVariableName(variableDeclInfo.getName())) {
      assert inLoopLimiterVariableDeclInfo;
      inLoopLimiterVariableDeclInfo = false;
    }
  }

  @Override
  public void visitArrayInfo(ArrayInfo arrayInfo) {
    // Do nothing: we do not want to simplify array size expressions.
  }

  boolean allowedToReduceExpr(IAstNode parent, Expr child) {

    if (child instanceof VariableIdentifierExpr) {
      final String name = ((VariableIdentifierExpr) child).getName();
      if (Constants.isLiveInjectedVariableName(name)
            && !Constants.isLooplimiterVariableName(name)) {
        return true;
      }
      if (name.startsWith(Constants.DEAD_PREFIX)) {
        return true;
      }
    }

    if (parent instanceof FunctionCallExpr) {
      final int childIndex = ((FunctionCallExpr) parent)
            .getArgs()
            .indexOf(child);
      for (FunctionPrototype fp : getEncounteredFunctionPrototypes().stream()
            .filter(item -> typer.prototypeMatches(item, (FunctionCallExpr) parent))
            .collect(Collectors.toList())) {
        if (fp.getParameter(childIndex).getType().hasQualifier(TypeQualifier.OUT_PARAM)
              || fp.getParameter(childIndex).getType().hasQualifier(TypeQualifier.INOUT_PARAM)) {
          return false;
        }
      }
    }

    if (context.reduceEverywhere()) {
      return true;
    }

    if (currentProgramPointIsDeadCode()) {
      return true;
    }

    if (injectionTracker.underFuzzedMacro()) {
      return true;
    }

    if (inLiveInjectedStmtOrDeclaration && !inLoopLimiterVariableDeclInfo
        && !loopLimiterImpactChecker.referencesNonRedundantLoopLimiter(child, getCurrentScope())) {
      // We can simplify expressions in live code, so long as they do not reference
      // (non-redundant) loop limiters, and are not related to loop limiter initialization.
      return true;
    }

    return false;

  }

}
