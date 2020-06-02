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
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.SetLayoutQualifier;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class LiteralToUniformReductionOpportunities {

  private LiteralToUniformReductionOpportunities() {
    // This class just provides a static methods.
  }

  static void replaceOpportunities(ShaderJob shaderJob) {

    // TODO what if there are other than fragment shaders?
    TranslationUnit tu = shaderJob.getShaders().get(0);
    PipelineInfo pipelineInfo = shaderJob.getPipelineInfo();


    // TODO separate lists for different types
    List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                    new IdGenerator()));

    // TODO check whether there are other uniforms already
    //int binding = this.pipelineInfo.getNumUniforms();
    int binding = 0;
    String uniformName = "values";

    List<Integer> literals = new ArrayList<>();
    for (LiteralToUniformReductionOpportunity o: ops) {
      literals.add(((IntConstantExpr)o.getLiteral()).getNumericValue());
    }

    // replace the literals with elements in an array
    ops.forEach(AbstractReductionOpportunity::applyReduction);

    // add uniform to the associated .json file
    BasicType type = BasicType.INT;
    pipelineInfo.addUniform(uniformName, type, Optional.of(literals.size()), literals);
    pipelineInfo.addUniformBinding(uniformName, binding);

    // Add layout qualifiers for the uniforms
    final ArrayType intArraySize = new ArrayType(BasicType.INT, new ArrayInfo(new IntConstantExpr(String.valueOf(ops.size()))));
    intArraySize.getArrayInfo().setConstantSizeExpr(2);

    tu.addDeclaration(
        new InterfaceBlock(
            Optional.of(new LayoutQualifierSequence(new SetLayoutQualifier(0),
                new BindingLayoutQualifier(binding))),
            TypeQualifier.UNIFORM,
            "buf" + binding,
            Collections.singletonList(uniformName),
            Collections.singletonList(intArraySize),
            Optional.empty()
        )
    );
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

          opportunities.add(new LiteralToUniformReductionOpportunity(tu, intConstantExpr,
              opportunities.size(),
              shaderJob.getPipelineInfo(), getVistitationDepth()));
        }

      }.visit(tu);
    }

    return opportunities;
  }

}
