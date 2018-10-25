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
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VariableDeclReductionOpportunitiesTest {

  @Test
  public void testRemoveUnusedLiveCodeDecl() throws Exception {
    final String program = "void main() {\n"
        + "  int GLF_live0c = 3;\n"
        + "}\n";
    final String reducedProgram = "void main() {\n"
        + "  int ;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testAnonymousStruct() throws Exception {
    final String program = "struct { int x; } b, c; void main() { b.x = 2; }\n";
    final String reducedProgram = "struct { int x; } b; void main() { b.x = 2; }\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testNamedStruct() throws Exception {
    final String program = "struct A { int x; } b; void main() { A c; c.x = 2; }\n";
    final String reducedProgram = "struct A { int x; }; void main() { A c; c.x = 2; }\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testNothingToRemove() throws Exception {
    final String program = "void main() {\n"
        + "  float k = 1.0;\n"
        + "  gl_FragColor = vec4(k, 0.0, 0.0, 0.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclReductionOpportunity> ops = VariableDeclReductionOpportunities
        .findOpportunities(
            MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(
                false,
                ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0),
                new IdGenerator(),
                true)
        );
    assertEquals(0, ops.size());
  }

}