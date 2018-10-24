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

package com.graphicsfuzz.common.ast.decl;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class VariablesDeclarationTest {

  @Test
  public void testSetDeclInfo() throws Exception {
    final String program = "int x, y; int main() { int y, z, w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new StandardVisitor() {
      @Override
      public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
        super.visitVariablesDeclaration(variablesDeclaration);
        for (int i = 0; i < variablesDeclaration.getNumDecls(); i++) {
          final VariableDeclInfo vdi = variablesDeclaration.getDeclInfo(i);
          variablesDeclaration.setDeclInfo(i, new VariableDeclInfo(vdi.getName() + "_modified",
              vdi.getArrayInfo(), vdi.getInitializer()));
        }
      }
    }.visit(tu);
    CompareAstsDuplicate.assertEqualAsts(
        "int x_modified, y_modified; int main() { int y_modified, z_modified, w_modified; }",
        tu);
  }


}