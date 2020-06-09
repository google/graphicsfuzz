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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class LiteralToUniformReductionOpportunity
    extends AbstractReductionOpportunity {

  private final ConstantExpr literalExpr;
  private final TranslationUnit translationUnit;
  private final ShaderJob shaderJob;


  LiteralToUniformReductionOpportunity(ConstantExpr literalExpr,
                                       TranslationUnit translationUnit,
                                       ShaderJob shaderJob,
                                       VisitationDepth depth) {

    super(depth);

    this.literalExpr = literalExpr;
    this.translationUnit = translationUnit;
    this.shaderJob = shaderJob;

    // If the uniform array doesn't exist in the pipeline info adds an empty array
    final String arrayName = Constants.INT_LITERAL_UNIFORM_VALUES;
    if (!shaderJob.getPipelineInfo().hasUniform(arrayName)) {
      shaderJob.getPipelineInfo().addUniform(arrayName, BasicType.INT,
          Optional.of(0), new ArrayList<>());
      shaderJob.getPipelineInfo().addUniformBinding(arrayName, false,
          shaderJob.getPipelineInfo().getNumUniforms());
    }
  }

  @Override
  void applyReductionImpl() {

    final String arrayName = Constants.INT_LITERAL_UNIFORM_VALUES;

    // Adds the literal to the array if it doesn't exist already and updates the index number to
    // point at the corresponding element in the array.
    final int index;
    final List<Number> values = this.shaderJob.getPipelineInfo().getArgs(arrayName);

    if (values.contains(((IntConstantExpr) this.literalExpr).getNumericValue())) {
      index = values.indexOf(((IntConstantExpr) this.literalExpr).getNumericValue());
    } else {
      index = this.shaderJob.getPipelineInfo().getArgs(arrayName).size();
      values.add(((IntConstantExpr) this.literalExpr).getNumericValue());
    }

    // Removes the old uniform array and adds the new one to the associated json structure.
    this.shaderJob.getPipelineInfo().removeUniform(arrayName);
    this.shaderJob.getPipelineInfo().addUniform(arrayName, BasicType.INT,
        Optional.of(values.size()), values);
    this.shaderJob.getPipelineInfo().addUniformBinding(arrayName, false,
        this.shaderJob.getPipelineInfo().getNumUniforms());

    // Adds the new array declaration to every translation unit.
    for (TranslationUnit tu: shaderJob.getShaders()) {

      // Declares the array if it didn't exist. Otherwise, removes it and adds a new declaration
      // with increased size.
      Optional<Declaration> oldDeclaration = Optional.empty();
      final List<VariablesDeclaration> declarations = this.translationUnit.getUniformDecls();
      for (VariablesDeclaration declaration : declarations) {
        if (declaration.getDeclInfo(0).getName().equals(arrayName)) {
          oldDeclaration = Optional.of(declaration);
        }
      }
      oldDeclaration.ifPresent(this.translationUnit::removeTopLevelDeclaration);

      final ArrayInfo arrayInfo = new ArrayInfo(new IntConstantExpr(String.valueOf(values.size())));
      final VariableDeclInfo variableDeclInfo = new VariableDeclInfo(arrayName,
          arrayInfo, null);
      final VariablesDeclaration asd = new VariablesDeclaration(
          new QualifiedType(BasicType.INT, Arrays.asList(TypeQualifier.UNIFORM)), variableDeclInfo
      );

      this.translationUnit.addDeclaration(asd);
    }

    // Replaces the literal with an element in the uniform array.
    final IParentMap parentMap = IParentMap.createParentMap(this.translationUnit);
    final ArrayIndexExpr aie = new ArrayIndexExpr(new VariableIdentifierExpr(arrayName),
        new IntConstantExpr(String.valueOf(index)));

    parentMap.getParent(this.literalExpr).replaceChild(this.literalExpr, aie);
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }

}
