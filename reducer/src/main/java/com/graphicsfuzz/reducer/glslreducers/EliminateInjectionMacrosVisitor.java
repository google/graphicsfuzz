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

package com.graphicsfuzz.reducer.glslreducers;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.reducer.reductionopportunities.MacroNames;

public class EliminateInjectionMacrosVisitor extends StandardVisitor {

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    super.visitBinaryExpr(binaryExpr);
    cleanUpMacros(binaryExpr);
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    super.visitUnaryExpr(unaryExpr);
    cleanUpMacros(unaryExpr);
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    super.visitParenExpr(parenExpr);
    cleanUpMacros(parenExpr);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);
    cleanUpMacros(functionCallExpr);
  }

  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    super.visitArrayIndexExpr(arrayIndexExpr);
    cleanUpMacros(arrayIndexExpr);
  }

  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    cleanUpMacros(memberLookupExpr);
  }

  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    super.visitTernaryExpr(ternaryExpr);
    cleanUpMacros(ternaryExpr);
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    cleanUpMacros(typeConstructorExpr);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    if (MacroNames.isDeadByConstruction(ifStmt.getCondition())
        || MacroNames.isIfWrapperFalse(ifStmt.getCondition())
        || MacroNames.isIfWrapperTrue(ifStmt.getCondition())) {
      ifStmt.setCondition(ifStmt.getCondition().getChild(0));
    }
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    super.visitSwitchStmt(switchStmt);
    if (MacroNames.isSwitch(switchStmt.getExpr())) {
      switchStmt.setExpr(switchStmt.getExpr().getChild(0));
    }
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    cleanupLoop(whileStmt);
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    super.visitExprStmt(exprStmt);
    if (MacroNames.isIdentity(exprStmt.getExpr())
        || MacroNames.isZero(exprStmt.getExpr())
        || MacroNames.isOne(exprStmt.getExpr())
        || MacroNames.isFalse(exprStmt.getExpr())
        || MacroNames.isTrue(exprStmt.getExpr())) {
      exprStmt.setExpr(exprStmt.getExpr().getChild(1));
    } else if (MacroNames.isFuzzed(exprStmt.getExpr())
        || MacroNames.isDeadByConstruction(exprStmt.getExpr())) {
      exprStmt.setExpr(exprStmt.getExpr().getChild(0));
    }
  }

  private void cleanupLoop(LoopStmt loopStmt) {
    if (MacroNames.isLoopWrapper(loopStmt.getCondition())) {
      loopStmt.setCondition(loopStmt.getCondition().getChild(0));
    }
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    cleanupLoop(doStmt);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    cleanupLoop(forStmt);
  }

  private void cleanUpMacros(Expr parent) {
    for (int i = 0; i < parent.getNumChildren(); i++) {
      Expr child = parent.getChild(i);
      // Note: it would be redundant to have a special function reduction opportunity
      // be classed also as a fuzzed expression reduction opportunity
      if (MacroNames.isIdentity(child)
          || MacroNames.isZero(child)
          || MacroNames.isOne(child)
          || MacroNames.isFalse(child)
          || MacroNames.isTrue(child)) {
        replaceChildWithGrandchild(parent, i, 1);
      } else if (MacroNames.isFuzzed(child)
          || MacroNames.isDeadByConstruction(child)) {
        replaceChildWithGrandchild(parent, i, 0);
      }
    }
  }

  @Override
  public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
    super.visitScalarInitializer(scalarInitializer);
    if (MacroNames.isIdentity(scalarInitializer.getExpr())
        || MacroNames.isZero(scalarInitializer.getExpr())
        || MacroNames.isOne(scalarInitializer.getExpr())
        || MacroNames.isFalse(scalarInitializer.getExpr())
        || MacroNames.isTrue(scalarInitializer.getExpr())) {
      scalarInitializer.setExpr(scalarInitializer.getExpr().getChild(1));
    } else if (MacroNames.isFuzzed(scalarInitializer.getExpr())
        || MacroNames.isDeadByConstruction(scalarInitializer.getExpr())) {
      scalarInitializer.setExpr(scalarInitializer.getExpr().getChild(0));
    }
  }

  private void replaceChildWithGrandchild(Expr parent, int childIndex, int grandchildIndex) {
    assert parent.getChild(childIndex).getNumChildren() > grandchildIndex;

    if (suitableToRemoveParentheses(parent, parent.getChild(childIndex))) {
      parent.setChild(childIndex, parent.getChild(childIndex).getChild(grandchildIndex));
    } else {
      parent.setChild(childIndex,
          new ParenExpr(parent.getChild(childIndex).getChild(grandchildIndex)));
    }
  }

  private boolean suitableToRemoveParentheses(IAstNode parent, Expr child) {

    if (child instanceof ConstantExpr
        || child instanceof VariableIdentifierExpr
        || child instanceof FunctionCallExpr) {
      // It's fine to remove parentheses in cases such as (5), (x) and (sin(a)).
      return true;
    }

    if (!(parent instanceof Expr)) {
      // These are outer-most parentheses; fine to remove them.
      return true;
    }
    if (parent instanceof ParenExpr) {
      // These are parentheses within parentheses; fine to remove them.
      return true;
    }
    if (parent instanceof FunctionCallExpr) {
      // These are parentheses under a function call argument.  Fine to remove them *unless*
      // they enclose a use of the comma operator; e.g. we don't want to turn sin((a, b)) into
      // sin(a, b).
      if (child instanceof BinaryExpr && ((BinaryExpr) child).getOp() == BinOp.COMMA) {
        return false;
      }
      return true;
    }
    // Conservatively say that it is not OK to remove parentheses.  We could be more aggressive
    // with attention to operator precedence.
    return false;
  }

}
