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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;

public class AddInitializers {

  /**
   * Adds an at-declaration initializer to every uninitialized variable in the given shader job,
   * unless it is not legitimate to initialize a variable (e.g. this is true for a uniform), or if
   * there is no way to make a canonical constant for the the variable's type (e.g., there is no
   * way to initialize a nameless struct at declaration).
   * @param shaderJob The shader job in which variables are to be initialized.
   */
  public static void addInitializers(ShaderJob shaderJob) {

    // Consider every shader in the shader job.
    for (TranslationUnit tu : shaderJob.getShaders()) {

      new ScopeTrackingVisitor() {

        @Override
        public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
          super.visitVariablesDeclaration(variablesDeclaration);

          // Do not initialize shader input/output variables, or uniforms.
          if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHADER_INPUT)
              || variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHADER_OUTPUT)
              || variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
            return;
          }
          for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
            if (vdi.hasInitializer()) {
              // There is already an initializer; nothing to do.
              continue;
            }
            // Work out the type of the variable.
            Type variableType = variablesDeclaration.getBaseType().getWithoutQualifiers();
            if (vdi.hasArrayInfo()) {
              variableType = new ArrayType(variableType, vdi.getArrayInfo());
            }
            if (!variableType.hasCanonicalConstant(getCurrentScope())) {
              // We don't know how to make a constant for this type, so we cannot add an
              // initializer.
              return;
            }
            // Add an initializer for this variable.
            vdi.setInitializer(new Initializer(
                variableType.getCanonicalConstant(getCurrentScope())));
          }
        }
      }.visit(tu);

    }

  }

}
