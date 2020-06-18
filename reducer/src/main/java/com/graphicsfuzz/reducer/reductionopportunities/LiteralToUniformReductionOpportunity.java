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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
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
  }

  @Override
  void applyReductionImpl() {

    final Number numericValue;
    final String arrayName;
    final BasicType basicType;

    // If the uniform array doesn't exist in the pipeline info, adds an empty array.
    if (literalExpr instanceof IntConstantExpr) {
      arrayName = Constants.INT_LITERAL_UNIFORM_VALUES;
      basicType = BasicType.INT;
      numericValue = ((IntConstantExpr) literalExpr).getNumericValue();
    } else {
      arrayName = Constants.FLOAT_LITERAL_UNIFORM_VALUES;
      basicType = BasicType.FLOAT;
      numericValue = Float.valueOf(((FloatConstantExpr) literalExpr).getValue());
    }

    if (!shaderJob.getPipelineInfo().hasUniform(arrayName)) {
      shaderJob.getPipelineInfo().addUniform(arrayName, basicType,
          Optional.of(0), new ArrayList<>());
      shaderJob.getPipelineInfo().addUniformBinding(arrayName, false,
          shaderJob.getPipelineInfo().getUnusedBindingNumber());
    }

    final int index;
    final List<Number> values = this.shaderJob.getPipelineInfo().getArgs(arrayName);

    if (values.contains(numericValue)) {
      index = values.indexOf(numericValue);
    } else {
      System.out.println(arrayName);
      System.out.println(numericValue);
      index = shaderJob.getPipelineInfo().appendValueToUniform(arrayName, numericValue);
    }

    // Adds the new array declaration to the current translation unit and updates the size
    // in every translation unit where the declaration is present.
    final int arraySize = shaderJob.getPipelineInfo().getArgs(arrayName).size();
    final ArrayInfo arrayInfo = new ArrayInfo(new IntConstantExpr(String.valueOf(arraySize)));
    final VariableDeclInfo variableDeclInfo = new VariableDeclInfo(arrayName,
        arrayInfo, null);
    final VariablesDeclaration arrayDecl = new VariablesDeclaration(
        new QualifiedType(basicType, Arrays.asList(TypeQualifier.UNIFORM)), variableDeclInfo
    );

    if (!this.translationUnit.hasUniformDeclaration(arrayName)) {
      this.translationUnit.addDeclaration(arrayDecl);
    }

    for (TranslationUnit tu: shaderJob.getShaders()) {
      if (tu.hasUniformDeclaration(arrayName)) {
        tu.updateTopLevelDeclaration(arrayDecl, tu.getUniformDeclaration(arrayName));
      }
    }

    // Replaces the literal with an access of the uniform.
    final IParentMap parentMap = IParentMap.createParentMap(this.translationUnit);
    final ArrayIndexExpr aie = new ArrayIndexExpr(new VariableIdentifierExpr(arrayName),
        new IntConstantExpr(String.valueOf(index)));

    if (parentMap.hasParent(this.literalExpr)) {
      parentMap.getParent(this.literalExpr).replaceChild(this.literalExpr, aie);
    }
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }

}
