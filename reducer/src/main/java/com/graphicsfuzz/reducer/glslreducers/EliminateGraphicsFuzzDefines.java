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
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.MacroNames;

public class EliminateGraphicsFuzzDefines extends StandardVisitor {

  private final ShadingLanguageVersion shadingLanguageVersion;

  private EliminateGraphicsFuzzDefines(ShadingLanguageVersion shadingLanguageVersion) {
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  /**
   * Eliminates all GraphicsFuzz macros from the given translation unit, by inlining them.
   * @param tu The translation unit to be processed.
   * @return A clone of the given translation unit, with all GraphicsFuzz macros inlined.
   */
  public static TranslationUnit transform(TranslationUnit tu) {
    final TranslationUnit result = tu.clone();
    new EliminateGraphicsFuzzDefines(tu.getShadingLanguageVersion()).visit(result);
    return result;
  }

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
        parent.replaceChild(functionCallExpr,
            addParenthesesIfNecessary(parent, functionCallExpr.getChild(1)));
      } else if (MacroNames.isFuzzed(functionCallExpr)
          || MacroNames.isDeadByConstruction(functionCallExpr)
          || MacroNames.isSwitch(functionCallExpr)
          || MacroNames.isLoopWrapper(functionCallExpr)
          || MacroNames.isIfWrapperFalse(functionCallExpr)
          || MacroNames.isIfWrapperTrue(functionCallExpr)) {
        parent.replaceChild(functionCallExpr,
            addParenthesesIfNecessary(parent, functionCallExpr.getChild(0)));
      } else if (MacroNames.isMakeInBoundsInt(functionCallExpr)) {
        expandMakeInBoundsInt(parent, functionCallExpr);
      } else if (MacroNames.isMakeInBoundsUint(functionCallExpr)) {
        expandMakeInBoundsUint(parent, functionCallExpr);
      }
    }
  }

  private void expandMakeInBoundsInt(IAstNode parent, FunctionCallExpr functionCallExpr) {

    // This replaces a call to _GLF_MAKE_IN_BOUNDS_INT with either:
    // - a call to clamp, if the shading language version supports integer clamping
    // - a ternary expression otherwise
    // The expression that is generated matches the definition of the macro, which can be found in
    // PrettyPrinterVisitor.emitGraphicsFuzzDefines.

    final ConstantExpr one = new IntConstantExpr("1");
    final ConstantExpr zero = new IntConstantExpr("0");
    if (shadingLanguageVersion.supportedClampInt()) {
      expandMakeInBoundsMacroToClamp(parent, functionCallExpr, one,
          zero);
    } else {
      final Expr indexLessThanZero =
          new BinaryExpr(new ParenExpr(functionCallExpr.getChild(0).clone()),
          zero, BinOp.LT);
      parent.replaceChild(functionCallExpr,
          addParenthesesIfNecessary(parent,
          new TernaryExpr(indexLessThanZero, zero.clone(),
              new ParenExpr(getTernaryUpperBoundCheckForMakeInBounds(functionCallExpr, one)))));
    }
  }

  private void expandMakeInBoundsUint(IAstNode parent, FunctionCallExpr functionCallExpr) {

    // Similar to expandMakeInBoundsInt, but for the 'uint' case.

    final ConstantExpr one = new UIntConstantExpr("1u");
    if (shadingLanguageVersion.supportedClampUint()) {
      expandMakeInBoundsMacroToClamp(parent, functionCallExpr, one,
          new UIntConstantExpr("0u"));
    } else {
      parent.replaceChild(functionCallExpr,
          addParenthesesIfNecessary(parent,
              getTernaryUpperBoundCheckForMakeInBounds(functionCallExpr,
                  one)));

    }
  }

  private Expr getTernaryUpperBoundCheckForMakeInBounds(FunctionCallExpr functionCallExpr,
                                                     ConstantExpr one) {
    return new TernaryExpr(new BinaryExpr(new ParenExpr(functionCallExpr.getChild(0).clone()),
        functionCallExpr.getChild(1).clone(),
        BinOp.GE),
        new BinaryExpr(functionCallExpr.getChild(1).clone(), one, BinOp.SUB),
        new ParenExpr(functionCallExpr.getChild(0).clone()));
  }

  private void expandMakeInBoundsMacroToClamp(IAstNode parent, FunctionCallExpr functionCallExpr,
                                              ConstantExpr one, ConstantExpr zero) {
    final Expr sizeMinusOne = new BinaryExpr(functionCallExpr.getChild(1),
        one,
        BinOp.SUB);
    final Expr clamp = new FunctionCallExpr("clamp", functionCallExpr.getChild(0),
        zero,
        sizeMinusOne);
    parent.replaceChild(functionCallExpr,
        addParenthesesIfNecessary(parent,
            clamp));
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
      // If parent is a parentheses expression, adding more parentheses would be
      // redundant,
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
