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
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
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
        public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
          // This prevents replacing the uniforms recursively. E.g. _GLF_int_values[0] does not
          // become _GLF_int_values[_GLF_int_values[1]].
          if (arrayIndexExpr.getArray() instanceof VariableIdentifierExpr) {
            String name = ((VariableIdentifierExpr) arrayIndexExpr.getArray()).getName();
            if (name.equals(Constants.FLOAT_LITERAL_UNIFORM_VALUES)
                || name.equals(Constants.INT_LITERAL_UNIFORM_VALUES)
                || name.equals(Constants.UINT_LITERAL_UNIFORM_VALUES)) {
              return;
            }
          }
          super.visitArrayIndexExpr(arrayIndexExpr);
        }

        @Override
        public void visitArrayInfo(ArrayInfo arrayInfo) {
          // Overriding this prevents replacing the size in array definitions, which must be
          // initialized with a constant expression, without descending into the internals of
          // the object in other visit methods.
        }

        @Override
        public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
          super.visitIntConstantExpr(intConstantExpr);
          opportunities.add(new LiteralToUniformReductionOpportunity(intConstantExpr, tu,
              shaderJob, getVistitationDepth()));
        }

        @Override
        public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
          super.visitFloatConstantExpr(floatConstantExpr);
          opportunities.add(new LiteralToUniformReductionOpportunity(floatConstantExpr, tu,
              shaderJob, getVistitationDepth()));
        }

        @Override
        public void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr) {
          super.visitUIntConstantExpr(uintConstantExpr);
          opportunities.add(new LiteralToUniformReductionOpportunity(uintConstantExpr, tu,
              shaderJob, getVistitationDepth()));
        }

      }.visit(tu);
    }

    return opportunities;
  }

}
