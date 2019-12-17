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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;

public class UpgradeShadingLanguageVersion extends ScopeTrackingVisitor {

  // The translation unit being upgraded
  private final TranslationUnit tu;

  // The shading language version to upgrade to.
  private final ShadingLanguageVersion newVersion;

  private UpgradeShadingLanguageVersion(TranslationUnit tu, ShadingLanguageVersion newVersion) {
    this.tu = tu;
    this.newVersion = newVersion;
    if (newVersion != ShadingLanguageVersion.ESSL_310) {
      throw new RuntimeException("Only upgrading to ESSL 310 supported at present.");
    }
    if (tu.getShadingLanguageVersion() != ShadingLanguageVersion.ESSL_100) {
      throw new RuntimeException("Only upgrading from ESSL 100 supported at present.");
    }

    // Traverse the translation unit to apply the upgrade to its content.
    visit(tu);

    if (tu.getShaderKind() == ShaderKind.FRAGMENT) {

      // Declare 'layout(location = 0) out vec4 _GLF_color;' at the start of the translation unit,
      // but after any initial precision declarations.

      // Find the first declaration that is not a precision declaration.
      Declaration firstNonPrecisionDeclaration = null;
      for (Declaration decl : tu.getTopLevelDeclarations()) {
        if (decl instanceof PrecisionDeclaration) {
          continue;
        }
        firstNonPrecisionDeclaration = decl;
        break;
      }

      // Add a declaration of '_GLF_color' before this declaration.
      tu.addDeclarationBefore(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC4, Arrays.asList(
              // 'layout(location = 0)'
              new LayoutQualifierSequence(new LocationLayoutQualifier(0)),
              // 'out'
              TypeQualifier.SHADER_OUTPUT
          )), new VariableDeclInfo(Constants.GLF_COLOR, null, null)),
          firstNonPrecisionDeclaration);
    }

    // Modify the claimed shading language version of the translation unit.
    tu.setShadingLanguageVersion(newVersion);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    // Rename occurrences of gl_FragColor.
    if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COLOR)) {
      variableIdentifierExpr.setName(Constants.GLF_COLOR);
    }
  }

  public static void upgrade(TranslationUnit tu, ShadingLanguageVersion newVersion) {
    new UpgradeShadingLanguageVersion(tu, newVersion);
  }

}
