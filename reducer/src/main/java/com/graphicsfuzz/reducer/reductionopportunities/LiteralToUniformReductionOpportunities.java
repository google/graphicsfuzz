/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.ArrayList;
import java.util.List;

public final class LiteralToUniformReductionOpportunities {

  private LiteralToUniformReductionOpportunities() {
    // This class just provides a static method.
  }

  static List<LiteralToUniformReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {

    final List<LiteralToUniformReductionOpportunity> opportunities = new ArrayList<>();

    for (TranslationUnit tu : shaderJob.getShaders()) {
      new StandardVisitor() {

        @Override
        public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
          super.visitIntConstantExpr(intConstantExpr);
          opportunities.add(new LiteralToUniformReductionOpportunity(intConstantExpr, tu,
              shaderJob, getVistitationDepth()));
        }

      }.visit(tu);
    }

    return opportunities;
  }

}
