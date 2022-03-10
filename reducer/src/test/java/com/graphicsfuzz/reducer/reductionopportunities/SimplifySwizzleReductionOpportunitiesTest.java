/*
 * Copyright 2022 The GraphicsFuzz Project Authors
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
import static org.junit.Assert.assertFalse;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

public class SimplifySwizzleReductionOpportunitiesTest {

  @Test
  public void testBasic() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.wwww.zzz.yy.x;\n"
        + "}\n";
    final String reducedProgram = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.xxxx.xxx.xx.x;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifySwizzleReductionOpportunity> ops = SimplifySwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(20, ops.size());
    for (SimplifySwizzleReductionOpportunity op : ops) {
      if (op.preconditionHolds()) {
        op.applyReduction();
      }
    }
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testDifferentComponentLetters() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.rgba.stpq.xyzw;\n"
        + "}\n";
    final String reducedProgram = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.rrrr.ssss.xxxx;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifySwizzleReductionOpportunity> ops = SimplifySwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(18, ops.size());
    for (SimplifySwizzleReductionOpportunity op : ops) {
      if (op.preconditionHolds()) {
        op.applyReduction();
      }
    }
    CompareAsts.assertEqualAsts(reducedProgram, tu);
  }

  @Test
  public void testLvalues() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.xy = vec2(1.0);\n"
        + "  v.rg = vec2(1.0);\n"
        + "  v.st = vec2(1.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifySwizzleReductionOpportunity> ops = SimplifySwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testLvalues2() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.yz = vec2(1.0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  vec4 v = vec2(1.0);\n"
        + "  v.xz = vec2(1.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifySwizzleReductionOpportunity> ops = SimplifySwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReductionImpl();
    assertFalse(ops.get(1).preconditionHolds());
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
