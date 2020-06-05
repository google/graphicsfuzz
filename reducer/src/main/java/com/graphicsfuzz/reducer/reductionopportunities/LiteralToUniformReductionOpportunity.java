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
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.BindingLayoutQualifier;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.SetLayoutQualifier;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LiteralToUniformReductionOpportunity
    extends AbstractReductionOpportunity {

  private final ConstantExpr literalExpr;
  private final ShaderJob shaderJob;


  LiteralToUniformReductionOpportunity(ConstantExpr literalExpr,
                                       ShaderJob shaderJob,
                                       VisitationDepth depth) {

    super(depth);

    this.literalExpr = literalExpr;
    this.shaderJob = shaderJob;

  }

  @Override
  void applyReductionImpl() {

    final String arrayName = Constants.INT_LITERAL_UNIFORM_VALUES;
    final List<Number> values;
    final int index;

    // Creates a combined list of the current and previously added values.
    // Updates the index number to point at the corresponding element in the array.
    if (this.shaderJob.getPipelineInfo().hasUniform(arrayName)) {
      values = this.shaderJob.getPipelineInfo().getArgs(arrayName);
      if (values.contains(((IntConstantExpr)this.literalExpr).getNumericValue())) {
        index = values.indexOf(((IntConstantExpr)this.literalExpr).getNumericValue());
      } else {
        index = this.shaderJob.getPipelineInfo().getArgs(arrayName).size();
        values.add(((IntConstantExpr)this.literalExpr).getNumericValue());
      }

    } else {
      values = new ArrayList<>();
      values.add(((IntConstantExpr)this.literalExpr).getNumericValue());
      index = values.size() - 1;
    }

    // Removes the old uniform array and adds the new one to the associated json structure.
    this.shaderJob.getPipelineInfo().removeUniform(arrayName);
    final int binding = this.shaderJob.getPipelineInfo().getNumUniforms();
    this.shaderJob.getPipelineInfo().addUniform(arrayName, BasicType.INT,
        Optional.of(values.size()), values);
    this.shaderJob.getPipelineInfo().addUniformBinding(arrayName, false, binding);

    // Adds declaration for the uniform array and replaces the literal with an element in the array.
    for (TranslationUnit tu: shaderJob.getShaders()) {

      // Declares the array if it didn't exist. Otherwise, removes it and adds a new declaration
      // with increased size.
      Optional<Declaration> oldDeclaration = Optional.empty();
      final List<Declaration> declarations = tu.getTopLevelDeclarations();
      for (Declaration declaration : declarations) {
        if (declaration instanceof InterfaceBlock) {
          final List<String> members = ((InterfaceBlock) declaration).getMemberNames();
          if (members.get(0).equals(arrayName)) {
            oldDeclaration = Optional.of(declaration);
          }
        }
      }

      oldDeclaration.ifPresent(tu::removeTopLevelDeclaration);

      final ArrayType arraySize = new ArrayType(BasicType.INT, new ArrayInfo(
          new IntConstantExpr(String.valueOf(values.size()))));

      arraySize.getArrayInfo().setConstantSizeExpr(2);
      final InterfaceBlock ib =
          new InterfaceBlock(Optional.of(new LayoutQualifierSequence(new SetLayoutQualifier(0),
              new BindingLayoutQualifier(binding))),
              TypeQualifier.UNIFORM,
              "buf" + binding,
              Collections.singletonList(arrayName),
              Collections.singletonList(arraySize),
              Optional.empty()
          );

      tu.addDeclaration(ib);

      // Replaces the literal with an element in the uniform array.
      final IParentMap parentMap = IParentMap.createParentMap(tu);
      final ArrayIndexExpr aie = new ArrayIndexExpr(new VariableIdentifierExpr(arrayName),
          new IntConstantExpr(String.valueOf(index)));

      parentMap.getParent(this.literalExpr).replaceChild(this.literalExpr,
          aie);
    }
  }

  @Override
  public boolean preconditionHolds() {
    return true;
  }

}
