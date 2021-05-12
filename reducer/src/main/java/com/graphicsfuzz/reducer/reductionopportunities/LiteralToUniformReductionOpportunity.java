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
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    if (literalExpr instanceof IntConstantExpr) {
      arrayName = Constants.INT_LITERAL_UNIFORM_VALUES;
      basicType = BasicType.INT;
      numericValue = ((IntConstantExpr) literalExpr).getNumericValue();
    } else if (literalExpr instanceof UIntConstantExpr) {
      arrayName = Constants.UINT_LITERAL_UNIFORM_VALUES;
      basicType = BasicType.UINT;
      numericValue = ((UIntConstantExpr) literalExpr).getNumericValue();
    } else {
      arrayName = Constants.FLOAT_LITERAL_UNIFORM_VALUES;
      basicType = BasicType.FLOAT;
      numericValue = Float.valueOf(((FloatConstantExpr) literalExpr).getValue());
    }

    // If the uniform array doesn't exist in the pipeline info, adds an empty array.
    if (!shaderJob.getPipelineInfo().hasUniform(arrayName)) {
      shaderJob.getPipelineInfo().addUniform(arrayName, basicType,
          Optional.of(0), new ArrayList<>());
    }

    final int index;
    final List<String> values = shaderJob.getPipelineInfo().getArgs(arrayName);

    if (values.contains(numericValue.toString())) {
      index = values.indexOf(numericValue.toString());
    } else {
      index = shaderJob.getPipelineInfo().appendValueToUniform(arrayName, numericValue);
    }

    final int arraySize = shaderJob.getPipelineInfo().getArgs(arrayName).size();
    final ArrayInfo arrayInfo =
        new ArrayInfo(Collections.singletonList(
            Optional.of(new IntConstantExpr(String.valueOf(arraySize)))));
    arrayInfo.setConstantSizeExpr(0, arraySize);
    final VariableDeclInfo variableDeclInfo = new VariableDeclInfo(arrayName,
        arrayInfo, null);
    final VariablesDeclaration arrayDecl = new VariablesDeclaration(
        new QualifiedType(basicType, Arrays.asList(TypeQualifier.UNIFORM)), variableDeclInfo
    );

    // Adds the new uniform array to the current translation unit in the case it doesn't exist.
    if (!translationUnit.hasUniformDeclaration(arrayName)) {
      translationUnit.addDeclaration(arrayDecl);
    }

    // Goes through each translation unit in the shader job and updates its existing uniform array
    // to refer this new uniform array.
    for (TranslationUnit tu : shaderJob.getShaders()) {
      if (tu.hasUniformDeclaration(arrayName)) {
        tu.updateTopLevelDeclaration(arrayDecl, tu.getUniformDeclaration(arrayName));
      }
    }

    // Replaces the literal with an access of the uniform.
    final IParentMap parentMap = IParentMap.createParentMap(translationUnit);
    final ArrayIndexExpr aie = new ArrayIndexExpr(new VariableIdentifierExpr(arrayName),
        new IntConstantExpr(String.valueOf(index)));

    if (parentMap.hasParent(literalExpr)) {
      parentMap.getParent(literalExpr).replaceChild(literalExpr, aie);
    }
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }

}
