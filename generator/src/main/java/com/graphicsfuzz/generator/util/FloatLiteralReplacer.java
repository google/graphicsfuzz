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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FloatLiteralReplacer extends StandardVisitor {

  private final IParentMap parentMap;
  private int uniformIndex;
  private final Map<String, Integer> literalToUniformIndex;

  /**
   * Replaces every occurrence of a floating-point literal in the translation unit with an access
   * to an array of uniforms, and notes the value expected at this uniform position in the
   * pipelineInfo structure.  The method may also adjust the shader to ensure that const
   * initializers are valid (by removing const and moving initializers), as the replacement of
   * floating-point literals changes whether intializers are compile-time constants.
   * @param tu Translation unit to be mutated
   * @param pipelineInfo Structure that holds pipeline state, including info about uniforms
   */
  public static void replace(TranslationUnit tu, PipelineInfo pipelineInfo) {
    FloatLiteralReplacer floatLiteralReplacer = new FloatLiteralReplacer(tu);
    ConstCleaner.clean(tu);
    List<Float> literals = new ArrayList<>();
    for (int i = 0; i < floatLiteralReplacer.uniformIndex; i++) {
      literals.add(null);
    }
    for (String s : floatLiteralReplacer.literalToUniformIndex.keySet()) {
      assert literals.get(floatLiteralReplacer.literalToUniformIndex.get(s)) == null;
      literals.set(floatLiteralReplacer.literalToUniformIndex.get(s), new Float(s));
    }
    if (literals.size() > 0) {
      pipelineInfo.addUniform(Constants.FLOAT_CONST, BasicType.FLOAT,
          Optional.of(literals.size()), literals);
    }
  }

  private FloatLiteralReplacer(TranslationUnit tu) {
    parentMap = IParentMap.createParentMap(tu);
    uniformIndex = 0;
    literalToUniformIndex = new HashMap<>();
    visit(tu);
    if (uniformIndex > 0) {
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.FLOAT, Arrays.asList(TypeQualifier.UNIFORM)),
          new VariableDeclInfo(Constants.FLOAT_CONST,
              new ArrayInfo(uniformIndex), null)
      ));
    }
  }

  @Override
  public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
    if (!literalToUniformIndex.containsKey(floatConstantExpr.getValue())) {
      literalToUniformIndex.put(floatConstantExpr.getValue(), uniformIndex);
      uniformIndex++;
    }
    parentMap.getParent(floatConstantExpr).replaceChild(floatConstantExpr,
        new ArrayIndexExpr(new VariableIdentifierExpr(Constants.FLOAT_CONST),
            new IntConstantExpr(Integer.toString(
                literalToUniformIndex.get(floatConstantExpr.getValue())))));
  }

}
