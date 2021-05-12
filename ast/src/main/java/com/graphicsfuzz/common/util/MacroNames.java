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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.util.Constants;

public class MacroNames {

  /**
   * Determines whether the given expression is an invocation of a GraphicsFuzz macro.
   *
   * @param expr an expression to be tested.
   * @return true if and only if the expression is an invocation of a GraphicsFuzz macro.
   */
  public static boolean isGraphicsFuzzMacro(Expr expr) {
    return isIdentity(expr)
        || isZero(expr)
        || isOne(expr)
        || isFalse(expr)
        || isTrue(expr)
        || isFuzzed(expr)
        || isDeadByConstruction(expr)
        || isLoopWrapper(expr)
        || isIfWrapperTrue(expr)
        || isIfWrapperFalse(expr)
        || isSwitch(expr)
        || isMakeInBoundsInt(expr)
        || isMakeInBoundsUint(expr);
  }

  public static boolean isIdentity(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_IDENTITY);
  }

  public static boolean isZero(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_ZERO);
  }

  public static boolean isOne(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_ONE);
  }

  public static boolean isFalse(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_FALSE);
  }

  public static boolean isTrue(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_TRUE);
  }

  public static boolean isFuzzed(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_FUZZED);
  }

  public static boolean isDeadByConstruction(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_DEAD);
  }

  public static boolean isLoopWrapper(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_WRAPPED_LOOP);
  }

  public static boolean isIfWrapperTrue(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_WRAPPED_IF_TRUE);
  }

  public static boolean isIfWrapperFalse(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_WRAPPED_IF_FALSE);
  }

  public static boolean isSwitch(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_SWITCH);
  }

  public static boolean isMakeInBoundsInt(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_MAKE_IN_BOUNDS_INT);
  }

  public static boolean isMakeInBoundsUint(Expr expr) {
    return isCallToNamedFunction(expr, Constants.GLF_MAKE_IN_BOUNDS_UINT);
  }

  private static boolean isCallToNamedFunction(Expr expr, String functionName) {
    return expr instanceof FunctionCallExpr && ((FunctionCallExpr) expr).getCallee()
        .equals(functionName);
  }

}
