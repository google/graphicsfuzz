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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.MacroNames;

public class EliminateInjectionMacrosVisitor extends StandardVisitor {

  @Override
  protected void visitChildFromParent(IAstNode child, IAstNode parent) {
    super.visitChildFromParent(child, parent);
    if (child instanceof FunctionCallExpr) {
      final FunctionCallExpr functionCallExpr = (FunctionCallExpr) child;
      if (MacroNames.isIdentity(functionCallExpr)
          || MacroNames.isZero(functionCallExpr)
          || MacroNames.isOne(functionCallExpr)
          || MacroNames.isFalse(functionCallExpr)
          || MacroNames.isTrue(functionCallExpr)) {
        parent.replaceChild(child,
            addParenthesesIfNecessary(parent, functionCallExpr.getChild(1)));
      } else if (MacroNames.isFuzzed(functionCallExpr)
          || MacroNames.isDeadByConstruction(functionCallExpr)
          || MacroNames.isSwitch(functionCallExpr)
          || MacroNames.isLoopWrapper(functionCallExpr)
          || MacroNames.isIfWrapperFalse(functionCallExpr)
          || MacroNames.isIfWrapperTrue(functionCallExpr)
      ) {
        parent.replaceChild(child,
            addParenthesesIfNecessary(parent, functionCallExpr.getChild(0)));
      }
    }
  }

  private IAstNode addParenthesesIfNecessary(IAstNode parent, Expr child) {
    if (child instanceof ConstantExpr
        || child instanceof ParenExpr
        || child instanceof VariableIdentifierExpr
        || child instanceof FunctionCallExpr) {
      // Parentheses is unnecessary in cases such as _GLF_FUNCTION(1),
      // _GLF_FUNCTION((1)), _GLF_FUNCTION(a), _GLF_FUNCTION(sin(a)).
      return child;
    }

    if (!(parent instanceof Expr)) {
      // No parentheses needed if the parent is not an expression,
      // for example, int x = _GLF_FUNCTION(a + b).
      return child;
    }

    if (parent instanceof ParenExpr) {
      // If parent is parentheses, adding a new parentheses would be redundant,
      // e.g. (_GLF_FUNCTION(a + b)).
      return child;
    }

    if (parent instanceof FunctionCallExpr) {
      // Parentheses is unnecessary if parent is a function call expression.
      // For example, foo(_GLF_FUNCTION(a)).

      // This asserts that the binary expression inside the function call is not a comma operator
      // as it is invalid to have a comma appear directly here, e.g. _GLF_IDENTITY(expr, a, b) is
      // not valid since a and b are treated as function arguments instead.
      assert (!(child instanceof BinaryExpr) || ((BinaryExpr) child).getOp() != BinOp.COMMA);
      return child;
    }

    return new ParenExpr(child);
  }

}
