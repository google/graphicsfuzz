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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * This class finds opportunities to remove initializers from global variable declarations and
 * replace them with assignment statements which will be inserted as the first statement in main
 * function. If main function is not found, do not consider finding this opportunities.
 * For example, in:
 * int a = 1;
 * int b = foo();
 * void main(){
 *  int c = 1;
 * }
 *
 * <p>Because each of a and b has an initializer, we can transform the code fragment
 * into the following:
 * int a;
 * int b;
 * void main(){
 *  a = 1;
 *  b = foo();
 *  int c = 1;
 * }
 */

public class GlobalVariableDeclToExprReductionOpportunities
    extends ReductionOpportunitiesBase<GlobalVariableDeclToExprReductionOpportunity> {
  private final List<VariablesDeclaration> globalVariableDecl;

  private GlobalVariableDeclToExprReductionOpportunities(TranslationUnit tu,
                                                        ReducerContext context) {
    super(tu, context);
    this.globalVariableDecl = new ArrayList<>();
  }

  /**
   * Find all initialized global variable declaration opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context   Includes info such as whether we reduce everywhere or only reduce injections
   * @return The opportunities that can be reduced
   */
  static List<GlobalVariableDeclToExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<GlobalVariableDeclToExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    GlobalVariableDeclToExprReductionOpportunities finder =
        new GlobalVariableDeclToExprReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }


  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    super.visitVariablesDeclaration(variablesDeclaration);
    if (!context.reduceEverywhere()) {
      // Replacing initializers with a new assignment statement might change semantics,
      // do not consider these reduction opportunities if we are not reducing everywhere.
      return;
    }

    // As constant must always be initialized, we will not consider global variable declarations
    // that have const qualifier (i.e., const int a = 1).
    if (atGlobalScope() && !variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.CONST)) {
      globalVariableDecl.add(variablesDeclaration);
    }
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    super.visitFunctionDefinition(functionDefinition);
    // We consider only the global variable declarations with the initializer that have been
    // found before main function.
    if (functionDefinition.getPrototype().getName().equals("main")) {
      for (VariablesDeclaration variablesDeclaration : globalVariableDecl) {
        for (VariableDeclInfo variableDeclInfo : variablesDeclaration.getDeclInfos()) {
          if (variableDeclInfo.hasInitializer()) {
            addOpportunity(new GlobalVariableDeclToExprReductionOpportunity(
                getVistitationDepth(), variableDeclInfo, functionDefinition));
          }
        }
      }
    }
  }
}
