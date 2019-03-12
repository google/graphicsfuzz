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
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.SideEffectChecker;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.List;

public abstract class ReductionOpportunitiesBase
      <ReductionOpportunityT extends IReductionOpportunity>
      extends ScopeTreeBuilder {

  private final List<ReductionOpportunityT> opportunities;
  protected final InjectionTracker injectionTracker;
  protected final NotReferencedFromLiveContext notReferencedFromLiveContext;
  protected final IParentMap parentMap;

  protected final ReducerContext context;

  protected String enclosingFunctionName;

  private int numEnclosingLValues;

  /**
   * Construct base class for finding reduction opportunities, with respect to a translation unit.
   * @param tu Translation unit that will subsequently be visited
   * @param context Includes information such as whether reductions should be sought everywhere or
   *                only to reverse transformations
   */
  public ReductionOpportunitiesBase(TranslationUnit tu, ReducerContext context) {
    this.opportunities = new ArrayList<>();
    this.injectionTracker = new InjectionTracker();
    this.notReferencedFromLiveContext = new NotReferencedFromLiveContext(tu);
    this.context = context;
    this.enclosingFunctionName = null;
    this.parentMap = IParentMap.createParentMap(tu);
    this.numEnclosingLValues = 0;
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert enclosingFunctionName == null;
    enclosingFunctionName = functionDefinition.getPrototype().getName();
    super.visitFunctionDefinition(functionDefinition);
    assert enclosingFunctionName == functionDefinition.getPrototype().getName();
    enclosingFunctionName = null;
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    injectionTracker.enterSwitch(MacroNames.isSwitch(switchStmt.getExpr()));
    super.visitSwitchStmt(switchStmt);
    injectionTracker.leaveSwitch(MacroNames.isSwitch(switchStmt.getExpr()));
  }

  @Override
  public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
    super.visitExprCaseLabel(exprCaseLabel);
    injectionTracker.notifySwitchCase(exprCaseLabel);
  }

  protected void visitChildOfBlock(BlockStmt block, int childIndex) {
    // Override in subclass if needed
  }

  @Override
  public void visitBlockStmt(BlockStmt block) {
    enterBlockStmt(block);

    for (int i = 0; i < block.getNumStmts(); i++) {

      visitChildOfBlock(block, i);

      final Stmt child = block.getStmt(i);

      if (child instanceof BreakStmt && parentMap.getParent(block) instanceof SwitchStmt) {
        injectionTracker.notifySwitchBreak();
      }

      if (isDeadCodeInjection(child)) {
        injectionTracker.enterDeadCodeInjection();
      }
      visit(child);
      if (isDeadCodeInjection(child)) {
        injectionTracker.exitDeadCodeInjection();
      }
    }
    leaveBlockStmt(block);
  }

  public static boolean isDeadCodeInjection(Stmt stmt) {
    return stmt instanceof IfStmt && MacroNames
        .isDeadByConstruction(((IfStmt) stmt).getCondition());
  }

  protected boolean enclosingFunctionIsDead() {
    if (enclosingFunctionName == null) {
      // We are in global scope
      return false;
    }
    return enclosingFunctionName.startsWith(Constants.DEAD_PREFIX)
        || notReferencedFromLiveContext.neverCalledFromLiveContext(enclosingFunctionName);
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    boolean isSideEffecting = binaryExpr.getOp().isSideEffecting();
    if (isSideEffecting) {
      enterLValueContext();
    }
    visitChildFromParent(binaryExpr.getLhs(), binaryExpr);
    if (isSideEffecting) {
      exitLValueContext();
    }
    visitChildFromParent(binaryExpr.getRhs(), binaryExpr);
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    if (unaryExpr.getOp().isSideEffecting()) {
      enterLValueContext();
    }
    super.visitUnaryExpr(unaryExpr);
    if (unaryExpr.getOp().isSideEffecting()) {
      exitLValueContext();
    }
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    if (MacroNames.isFuzzed(functionCallExpr)) {
      injectionTracker.enterFuzzedMacro();
    }
    if (MacroNames.isDeadByConstruction(functionCallExpr)) {
      injectionTracker.enterDeadMacro();
    }
    super.visitFunctionCallExpr(functionCallExpr);
    if (MacroNames.isDeadByConstruction(functionCallExpr)) {
      injectionTracker.exitDeadMacro();
    }
    if (MacroNames.isFuzzed(functionCallExpr)) {
      injectionTracker.exitFuzzedMacro();
    }
  }

  @Override
  protected void visitChildFromParent(IAstNode child, IAstNode parent) {
    super.visitChildFromParent(child, parent);
    if (child instanceof Expr) {
      identifyReductionOpportunitiesForChild(parent, (Expr) child);
    }
  }

  void identifyReductionOpportunitiesForChild(IAstNode parent, Expr child) {
    // Override in subclass if needed
  }

  private void enterLValueContext() {
    numEnclosingLValues++;
  }

  private void exitLValueContext() {
    assert numEnclosingLValues > 0;
    numEnclosingLValues--;
  }

  boolean inLValueContext() {
    return numEnclosingLValues > 0;
  }

  boolean initializerIsScalarAndSideEffectFree(VariableDeclInfo variableDeclInfo) {
    if (!variableDeclInfo.hasInitializer()) {
      return false;
    }
    if (!(variableDeclInfo.getInitializer() instanceof ScalarInitializer)) {
      return false;
    }
    return SideEffectChecker.isSideEffectFree(
        ((ScalarInitializer) variableDeclInfo.getInitializer()).getExpr(),
        context.getShadingLanguageVersion());
  }

  boolean typeIsReducibleToConst(Type type) {
    return type != null && type.hasCanonicalConstant();
  }

  boolean isFullyReducedConstant(Expr expr) {
    if (expr instanceof ConstantExpr) {
      return true;
    }
    if (!(expr instanceof TypeConstructorExpr)) {
      return false;
    }
    TypeConstructorExpr tce = (TypeConstructorExpr) expr;
    for (int i = 0; i < tce.getNumChildren(); i++) {
      if (!isFullyReducedConstant(tce.getChild(i))) {
        return false;
      }
    }
    return true;
  }

  public final List<ReductionOpportunityT> getOpportunities() {
    return opportunities;
  }

  final void addOpportunity(
        ReductionOpportunityT opportunity) {
    if (opportunity.preconditionHolds()) {
      // Guard against the possibility of us adding a reduction opportunity whose precondition
      // does not hold -- this would lead to a reduction loop.
      opportunities.add(opportunity);
    }
  }

}
