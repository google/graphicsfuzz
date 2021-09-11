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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.TyperHelper;

public class SideEffectChecker {

  private static boolean isSideEffectFreeVisitor(IAstNode node,
      ShadingLanguageVersion shadingLanguageVersion, ShaderKind shaderKind) {
    return !new CheckPredicateVisitor() {

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        if (TyperHelper.getBuiltins(shadingLanguageVersion, false, shaderKind)
            .containsKey(functionCallExpr.getCallee())) {
          for (FunctionPrototype p :
              TyperHelper.getBuiltins(shadingLanguageVersion, false, shaderKind)
                  .get(functionCallExpr.getCallee())) {
            // We check each argument of the built-in's prototypes to see if they require lvalues -
            // if so, they can cause side effects.
            // We could be more precise here by finding the specific overload of the function rather
            // than checking every possible prototype for lvalue parameters.
            for (ParameterDecl param : p.getParameters()) {
              if (param.getType().hasQualifier(TypeQualifier.OUT_PARAM)
                  || param.getType().hasQualifier(TypeQualifier.INOUT_PARAM)) {
                predicateHolds();
              }
            }
          }
        } else if (!MacroNames.isGraphicsFuzzMacro(functionCallExpr)) {
          // Assume that any call to a function that is not a GraphicsFuzz macro or builtin
          // might have a side-effect.
          predicateHolds();
        }
        super.visitFunctionCallExpr(functionCallExpr);
      }

      @Override
      public void visitUnaryExpr(UnaryExpr unaryExpr) {
        if (unaryExpr.getOp().isSideEffecting()) {
          predicateHolds();
        }
        super.visitUnaryExpr(unaryExpr);
      }

      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        if (binaryExpr.getOp().isSideEffecting()) {
          predicateHolds();
        }
        super.visitBinaryExpr(binaryExpr);
      }

      @Override
      public void visitReturnStmt(ReturnStmt returnStmt) {
        predicateHolds();
      }

      @Override
      public void visitBreakStmt(BreakStmt breakStmt) {
        predicateHolds();
      }

      @Override
      public void visitContinueStmt(ContinueStmt continueStmt) {
        predicateHolds();
      }

      @Override
      public void visitDiscardStmt(DiscardStmt discardStmt) {
        predicateHolds();
      }

      @Override
      public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
        predicateHolds();
      }

      @Override
      public void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel) {
        predicateHolds();
      }

    }.test(node);
  }

  public static boolean isSideEffectFree(Stmt stmt, ShadingLanguageVersion shadingLanguageVersion,
                                         ShaderKind shaderKind) {
    return isSideEffectFreeVisitor(stmt, shadingLanguageVersion, shaderKind);
  }

  public static boolean isSideEffectFree(Expr expr, ShadingLanguageVersion shadingLanguageVersion,
                                         ShaderKind shaderKind) {
    return isSideEffectFreeVisitor(expr, shadingLanguageVersion, shaderKind);
  }

}
