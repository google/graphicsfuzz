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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.Arrays;

public class AvoidDeprecatedGlFragColor extends StandardVisitor {

  private final String colorName;

  private AvoidDeprecatedGlFragColor(String colorName) {
    this.colorName = colorName;
  }

  /**
   * This *mutator* method changes all occurrences of gl_FragColor to use the given name.
   * @param tu The translation unit to be mutated.
   * @param colorName The replacement name for gl_FragColor.
   */
  public static void avoidDeprecatedGlFragColor(TranslationUnit tu,
        String colorName) {
    new AvoidDeprecatedGlFragColor(colorName).visit(tu);
    tu.addDeclaration(new VariablesDeclaration(
        new QualifiedType(BasicType.VEC4, Arrays.asList(
            new LayoutQualifier("location = 0"), TypeQualifier.SHADER_OUTPUT)),
        new VariableDeclInfo(colorName, null, null)
    ));
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COLOR)) {
      variableIdentifierExpr.setName(colorName);
    }
  }

}
