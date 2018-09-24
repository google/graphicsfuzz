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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class RemoveUnusedParameterReductionOpportunity extends AbstractReductionOpportunity {

  private final TranslationUnit translationUnit;
  private final FunctionDefinition functionDefinition;
  private final ParameterDecl parameterDecl;

  RemoveUnusedParameterReductionOpportunity(TranslationUnit translationUnit,
                                            FunctionDefinition functionDefinition,
                                            ParameterDecl parameterDecl,
                                            VisitationDepth depth) {
    super(depth);
    this.translationUnit = translationUnit;
    this.functionDefinition = functionDefinition;
    this.parameterDecl = parameterDecl;
  }

  @Override
  void applyReductionImpl() {
    final int paramIndex = functionDefinition.getPrototype().getParameters().indexOf(parameterDecl);
    new StandardVisitor() {

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        if (functionCallExpr.getCallee().equals(functionDefinition.getPrototype().getName())) {
          // As we only apply this reduction opportunity if the function name is not overloaded,
          // this has to be a call to the relevant function.
          functionCallExpr.removeArg(paramIndex);
        }
      }

      @Override
      public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
        super.visitFunctionPrototype(functionPrototype);
        if (functionPrototype.getName().equals(functionDefinition.getPrototype().getName())) {
          // As we only apply this reduction opportunity if the function name is not overloaded,
          // this has to be a prototype for the relevant function (there could be multiple identical
          // prototypes).
          functionPrototype.removeParameter(paramIndex);
        }
      }

    }.visit(translationUnit);
  }

  @Override
  public boolean preconditionHolds() {
    return functionDefinition.getPrototype().getParameters().contains(parameterDecl);
  }
}
