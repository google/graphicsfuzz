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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.mutateapi.MutationFinder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MutationFinderBase<MutationT extends Mutation>
      extends ScopeTreeBuilder implements MutationFinder<MutationT> {

  private final TranslationUnit tu;
  private final List<MutationT> mutations;
  protected boolean underForLoopHeader;

  public MutationFinderBase(TranslationUnit tu) {
    this.tu = tu;
    this.mutations = new ArrayList<>();
    this.underForLoopHeader = false;
  }

  @Override
  public final List<MutationT> findMutations() {
    visit(tu);
    return Collections.unmodifiableList(mutations);
  }

  /**
   * Yields the translation unit for which mutation opportunities are being sought.
   * @return The translation unit under analysis.
   */
  protected TranslationUnit getTranslationUnit() {
    return tu;
  }

  protected final void addMutation(MutationT mutation) {
    mutations.add(mutation);
  }

  /**
   * <p>
   *   A hook to allow an action to be performed when a child statement is visited from a block.
   * </p>
   * <p>
   *   The base class method should not be called directly.
   * </p>
   * <p>
   *   The method is not abstract, because not all child classes will need to exploit this hook.
   * </p>
   * @param parent A block that contains child as a statement.
   * @param child A statement inside the given block.
   */
  protected void visitChildOfBlock(BlockStmt parent, Stmt child) {
    // Override in subclass if needed
  }

  /**
   * <p>
   *   A hook to allow an action to be performed when a child expression is visited from a parent
   *   expression.
   * </p>
   * <p>
   *   The base class method should not be called directly.
   * </p>
   * <p>
   *   The method is not abstract, because not all child classes will need to exploit this hook.
   * </p>
   * @param parent An expression that has a child at the given index.
   * @param childIndex The index of a child of the parent.
   */
  protected void visitChildOfExpr(Expr parent, int childIndex) {
    // Override in subclass if needed
  }

  /**
   * <p>
   *   A hook to allow an action to be performed when an arbitrary expression is visited.
   * </p>
   * <p>
   *   The base class method should not be called directly.
   * </p>
   * <p>
   *   The method is not abstract, because not all child classes will need to exploit this hook.
   * </p>
   * @param expr The visited expression.
   */
  protected void visitExpr(Expr expr) {
    // Override in subclass if needed
  }

  /**
   * Invoke hook method for each statement  of the given block.
   * @param block Block to be visited.
   */
  @Override
  public void visitBlockStmt(BlockStmt block) {
    enterBlockStmt(block);
    for (Stmt child : block.getStmts()) {
      visitChildOfBlock(block, child);
      visit(child);
    }
    leaveBlockStmt(block);
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    super.visitBinaryExpr(binaryExpr);
    visitExpr(binaryExpr);
    visitChildrenOfExpr(binaryExpr);
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    super.visitUnaryExpr(unaryExpr);
    visitExpr(unaryExpr);
    visitChildrenOfExpr(unaryExpr);
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    super.visitParenExpr(parenExpr);
    visitExpr(parenExpr);
    visitChildrenOfExpr(parenExpr);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);
    visitExpr(functionCallExpr);
    visitChildrenOfExpr(functionCallExpr);
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    super.visitArrayIndexExpr(arrayIndexExpr);
    visitExpr(arrayIndexExpr);
    visitChildrenOfExpr(arrayIndexExpr);
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    visitExpr(memberLookupExpr);
    visitChildrenOfExpr(memberLookupExpr);
  }

  @Override
  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    super.visitTernaryExpr(ternaryExpr);
    visitExpr(ternaryExpr);
    visitChildrenOfExpr(ternaryExpr);
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    visitExpr(typeConstructorExpr);
    visitChildrenOfExpr(typeConstructorExpr);
  }

  @Override
  public void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr) {
    super.visitBoolConstantExpr(boolConstantExpr);
    visitExpr(boolConstantExpr);
  }

  @Override
  public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
    super.visitIntConstantExpr(intConstantExpr);
    visitExpr(intConstantExpr);
  }

  @Override
  public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
    super.visitFloatConstantExpr(floatConstantExpr);
    visitExpr(floatConstantExpr);
  }

  @Override
  public void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr) {
    super.visitUIntConstantExpr(uintConstantExpr);
    visitExpr(uintConstantExpr);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    visitExpr(variableIdentifierExpr);
  }

  private void visitChildrenOfExpr(Expr expr) {
    for (int i = 0; i < expr.getNumChildren(); i++) {
      visitChildOfExpr(expr, i);
    }
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    pushScope();
    assert !underForLoopHeader;
    underForLoopHeader = true;
    visitChildFromParent(forStmt.getInit(), forStmt);
    visitChildFromParent(forStmt.getCondition(), forStmt);
    visitChildFromParent(forStmt.getIncrement(), forStmt);
    assert underForLoopHeader;
    underForLoopHeader = false;
    visitChildFromParent(forStmt.getBody(), forStmt);
    popScope();
  }
}
