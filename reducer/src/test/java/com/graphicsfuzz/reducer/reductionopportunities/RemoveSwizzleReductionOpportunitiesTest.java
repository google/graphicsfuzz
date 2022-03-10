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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

public class RemoveSwizzleReductionOpportunitiesTest {

  @Test
  public void testBasic() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  vec2 v = vec2(1.0);\n"
        + "  v.xyz.xz.x;\n"
        + "}\n";
    final String step1ReducedProgram = "#version 310 es\n"
        + "void main() {\n"
        + "  vec2 v = vec2(1.0);\n"
        + "  v.xyz.x;\n"
        + "}\n";
    final String step2ReducedProgram = "#version 310 es\n"
        + "void main() {\n"
        + "  vec2 v = vec2(1.0);\n"
        + "  v.x;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<RemoveSwizzleReductionOpportunity> ops = RemoveSwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(step1ReducedProgram, tu);
    ops = RemoveSwizzleReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(step2ReducedProgram, tu);
  }

}
