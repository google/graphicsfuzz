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

  @Override
  protected void visitChildFromParent(IAstNode child, IAstNode parent) {
    super.visitChildFromParent(child, parent);
    if (child instanceof FunctionCallExpr) {
      Expr expr = (Expr) child;
      if (MacroNames.isIdentity(expr)
          || MacroNames.isZero(expr)
          || MacroNames.isOne(expr)
          || MacroNames.isFalse(expr)
          || MacroNames.isTrue(expr)) {
        parent.replaceChild(child,
            addParenthesesIfNecessary(parent, ((FunctionCallExpr) child).getChild(1)));
      } else if (MacroNames.isFuzzed(expr)
          || MacroNames.isDeadByConstruction(expr)) {
        parent.replaceChild(child,
            addParenthesesIfNecessary(parent, ((FunctionCallExpr) child).getChild(0)));
      }
    }
  }

  private IAstNode addParenthesesIfNecessary(IAstNode parent, Expr child) {
    if (ifParenthesesNecessary(parent, child)) {
      return new ParenExpr(child);
    }
    return child;
  }

  boolean ifParenthesesNecessary(IAstNode parent, Expr child) {
    if (child instanceof ConstantExpr
        || child instanceof ParenExpr
        || child instanceof VariableIdentifierExpr
        || child instanceof FunctionCallExpr) {
      // Parentheses is unnecessary in cases such as _GLF_FUNCTION(1),
      // _GLF_FUNCTION((1)), _GLF_FUNCTION(a), _GLF_FUNCTION(sin(a)).
      return false;
    }

    if (!(parent instanceof Expr)) {
      // No parentheses needed if the parent is not an expression,
      // for example, int x = _GLF_FUNCTION(a + b).
      return false;
    }

    if (parent instanceof ParenExpr) {
      // If parent is parentheses, adding a new parentheses would be redundant,
      // e.g. (_GLF_FUNCTION(a + b)).
      return false;
    }

    if (parent instanceof FunctionCallExpr) {
      if (child instanceof BinaryExpr) {
        // The binary operator must not be the comma operator
        // as it cannot appear directly as a macro argument.
        assert ((BinaryExpr) child).getOp() != BinOp.COMMA;
        return true;
      }
      return false;
    }

    return true;
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

  private void replaceChildWithGrandchild(Expr parent, int childIndex, int grandchildIndex) {
    assert parent.getChild(childIndex).getNumChildren() > grandchildIndex;
    parent.setChild(0, (Expr) addParenthesesIfNecessary(parent,
        parent.getChild(childIndex).getChild(grandchildIndex)));
  }
}
