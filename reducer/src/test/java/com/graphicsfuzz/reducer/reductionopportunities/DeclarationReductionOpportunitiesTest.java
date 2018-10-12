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

package com.graphicsfuzz.reducer.reductionopportunities;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.util.List;
import org.junit.Test;

public class DeclarationReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Test
  public void testNothingToRemove() throws Exception {
    final String program = "void main() {\n"
          + "  float k = 1.0;\n"
          + "  gl_FragColor = vec4(k, 0.0, 0.0, 0.0);\n"
          + "}\n";
    final TranslationUnit tu = Helper.parse(program, false);
    List<IReductionOpportunity> ops = ReductionOpportunities
          .getReductionOpportunities(
              MakeShaderJobFromFragmentShader.make(tu),
              new ReductionOpportunityContext(
                  false,
                  ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0),
                  new IdGenerator()
              ),
              fileOps
          );
    assertEquals(0, ops.size());
  }

  @Test
  public void testDoNotRemoveLocalInitialization() throws Exception {
    final String program = "float f;\n"
          + "float foo() {\n"
          + "  f = 2.0;\n"
          + "  return 1.0;\n"
          + "}\n"
          + "void main() {\n"
          + "  float k = foo();\n"
          + "  gl_FragColor = vec4(f, 0.0, 0.0, 0.0);\n"
          + "}\n";
    final TranslationUnit tu = Helper.parse(program, false);
    List<IReductionOpportunity> ops = ReductionOpportunities
          .getReductionOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()), fileOps);
    assertEquals(0, ops.size());
  }

  @Test
  public void testDoNotRemoveUsedLiveCodeDecl() throws Exception {
    final String program = "void main() {\n"
          + "  int GLF_live0c = 3;"
          + "  GLF_live0c++;"
          + "}\n";
    final TranslationUnit tu = Helper.parse(program, false);
    List<? extends IReductionOpportunity> ops = DeclarationReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null));
    assertEquals(0, ops.size());
  }

  @Test
  public void testRemoveUnusedLiveCodeDecl() throws Exception {
    final String program = "void main() {\n"
          + "  int GLF_live0c = 3;"
          + "}\n";
    final String reducedProgram = "void main() {\n"
          + "}\n";
    final TranslationUnit tu = Helper.parse(program, false);
    List<? extends IReductionOpportunity> ops = DeclarationReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(reducedProgram, false)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testRemoveDeclarationsInUnreachableFunction() throws Exception {
    final String program = "void f()\n"
          + "{\n"
          + " float a = 17. + 1.0;\n"
          + " float b = 0.;\n"
          + " float c = 0.;\n"
          + " float d = 0.;\n"
          + "}\n"
          + "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(program, false);
    final List<DeclarationReductionOpportunity> ops = DeclarationReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null));
    assertEquals(4, ops.size());
  }

}