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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GlobalVariablesDeclarationReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

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
    final TranslationUnit tu = ParseHelper.parse(program);
    List<IReductionOpportunity> ops = ReductionOpportunities
          .getReductionOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator(), true), fileOps);
    assertEquals(0, ops.size());
  }

  @Test
  public void testDoNotRemoveUsedLiveCodeDecl() throws Exception {
    final String program = "void main() {\n"
          + "  int GLF_live0c = 3;"
          + "  GLF_live0c++;"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops = GlobalVariablesDeclarationReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testUsedStructIsNotRemoved() throws Exception {
    final String program = "struct S { float x; };\n"
        + "void main() {\n"
        + " gl_FragColor = vec4(S(3.0).x);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<GlobalVariablesDeclarationReductionOpportunity> ops = GlobalVariablesDeclarationReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testUnusedStructIsRemoved() throws Exception {
    final String original = "struct S { int x; }; void main() { }";
    final String expected = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariablesDeclarationReductionOpportunity> ops = GlobalVariablesDeclarationReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testUsedAnonymousStructIsNotRemoved() throws Exception {
    final String program = "const struct { float x; } a;\n"
        + "void main() {\n"
        + " gl_FragColor = vec4(a.x);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<GlobalVariablesDeclarationReductionOpportunity> ops = GlobalVariablesDeclarationReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testUnusedAnonymousStructIsRemoved() throws Exception {
    final String original = "volatile struct { int x; }; void main() { }";
    final String expected = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariablesDeclarationReductionOpportunity> ops = GlobalVariablesDeclarationReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testVarIsRemovedButStructLeftBehind() throws Exception {
    final String original = "struct S { float x; } myS; void main() { gl_FragColor = vec4(S(3.0)" +
        ".x); }";
    final String expected = "struct S { float x; } ;    void main() { gl_FragColor = vec4(S(3.0)" +
        ".x); }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
    final List<GlobalVariablesDeclarationReductionOpportunity> moreOps =
        GlobalVariablesDeclarationReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(0, moreOps.size());
  }

}