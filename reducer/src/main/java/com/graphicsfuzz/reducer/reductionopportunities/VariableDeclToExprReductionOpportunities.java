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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;

/*
 * This class finds opportunities to remove initializers from variable declarations and
 * replace them with assignment statements following the declaration. For example, in:
 * int a = 1;
 * int b = foo();
 * int c;
 *
 * <p>Because each of a and b has an initializer, we can transform the code fragment
 * into the following:
 * int a;
 * a = 1;
 * int b;
 * b = foo();
 * int c;
 */

public class VariableDeclToExprReductionOpportunities
    extends ReductionOpportunitiesBase<VariableDeclToExprReductionOpportunity> {

  private VariableDeclToExprReductionOpportunities(TranslationUnit tu, ReducerContext context) {
    super(tu, context);
  }

  /**
   * Find all initialized variable declaration opportunities for the given translation unit.
   *
   * @param shaderJob The shader job to be searched.
   * @param context   Includes info such as whether we reduce everywhere or only reduce injections
   * @return The opportunities that can be reduced
   */
  static List<VariableDeclToExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<VariableDeclToExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    VariableDeclToExprReductionOpportunities finder =
        new VariableDeclToExprReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    super.visitDeclarationStmt(declarationStmt);
    if (!context.reduceEverywhere()) {
      // Replacing initializers with a new assignment statement might have a side-effect,
      // do not consider these reduction opportunities if we are not reducing everywhere.
      return;
    }

    if (declarationStmt.getVariablesDeclaration().getBaseType().hasQualifier(TypeQualifier.CONST)) {
      // A constant must always be initialized, so do not consider variable declarations that
      // have const qualifier (i.e., const int a = 1).
      return;
    }
    final List<VariableDeclInfo> declInfos =
        declarationStmt.getVariablesDeclaration().getDeclInfos();
    // We iterate backwards so that when applying reduction the new expression is inserted at the
    // correct order with respect to its original order in the variable declaration info list.
    for (int i = declInfos.size() - 1; i >= 0; i--) {
      final VariableDeclInfo variableDeclInfo = declInfos.get(i);
      if (variableDeclInfo.hasInitializer()) {
        addOpportunity(new VariableDeclToExprReductionOpportunity(
            variableDeclInfo,
            currentBlock(),
            declarationStmt,
            getVistitationDepth()));
      }
    }
  }
}
