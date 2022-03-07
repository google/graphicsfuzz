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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.SideEffectChecker;
import java.util.ArrayList;
import java.util.List;

public abstract class ReductionOpportunitiesBase
      <ReductionOpportunityT extends IReductionOpportunity>
      extends InjectionTrackingVisitor {

  private final List<ReductionOpportunityT> opportunities;

  protected final ReducerContext context;

  final ShaderKind shaderKind;

  // Each entry in this stack records how many l-values enclose the current expression. A new
  // stack entry is pushed each time an array indexing expression is encountered, because even when
  // an array indexing expression is an l-value, this does not mean that the index expression
  // itself is an l-value.
  private final List<Integer> enclosingLValuesStack;

  /**
   * Construct base class for finding reduction opportunities, with respect to a translation unit.
   * @param tu Translation unit that will subsequently be visited
   * @param context Includes information such as whether reductions should be sought everywhere or
   *                only to reverse transformations
   */
  public ReductionOpportunitiesBase(TranslationUnit tu, ReducerContext context) {
    super(tu);
    this.opportunities = new ArrayList<>();
    this.context = context;
    this.shaderKind = tu.getShaderKind();
    this.enclosingLValuesStack = new ArrayList<>();
    this.enclosingLValuesStack.add(0);
  }

  @Override
  public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
    // Do *not* invoke super.visitExprCaseLabel(...), as we do not wish to look for opportunities
    // to simplify the literal expression that is used as a case label.  Literals are simple enough
    // already, and attempting to change the value of a case label literal runs the risk of
    // introducing duplicate case labels.
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
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    enclosingLValuesStack.add(0);
    super.visitArrayIndexExpr(arrayIndexExpr);
    enclosingLValuesStack.remove(enclosingLValuesStack.size() - 1);
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
    enclosingLValuesStack.set(enclosingLValuesStack.size() - 1,
        enclosingLValuesStack.get(enclosingLValuesStack.size() - 1) + 1);
  }

  private void exitLValueContext() {
    assert enclosingLValuesStack.get(enclosingLValuesStack.size() - 1) > 0;
    enclosingLValuesStack.set(enclosingLValuesStack.size() - 1,
        enclosingLValuesStack.get(enclosingLValuesStack.size() - 1) - 1);
  }

  boolean inLValueContext() {
    return enclosingLValuesStack.get(enclosingLValuesStack.size() - 1) > 0;
  }

  boolean initializerIsScalarAndSideEffectFree(VariableDeclInfo variableDeclInfo) {
    if (!variableDeclInfo.hasInitializer()) {
      return false;
    }
    return SideEffectChecker.isSideEffectFree(
        (variableDeclInfo.getInitializer()).getExpr(),
        context.getShadingLanguageVersion(),
        shaderKind);
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
