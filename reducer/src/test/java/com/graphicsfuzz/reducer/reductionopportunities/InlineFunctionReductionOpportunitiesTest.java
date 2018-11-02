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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class InlineFunctionReductionOpportunitiesTest {

  @Test
  public void testBasicInline() throws Exception {

    final String program = "int inlineme(int x) { int y = 2; return y + x; }" +
        "void main() { int z = inlineme(5); }";

    final String expected = "int inlineme(int x) { int y = 2; return y + x; }" +
        "void main() { " +
        " int inlineme_inline_return_value_0;" +
        " {" +
        "  int x = 5;" +
        "  int y = 2;" +
        "  inlineme_inline_return_value_0 = y + x;" +
        " }" +
        " int z = inlineme_inline_return_value_0;" +
        "}";

    final TranslationUnit tu = ParseHelper.parse(program);
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(tu);

    final List<InlineFunctionReductionOpportunity> ops =
        InlineFunctionReductionOpportunities.findOpportunities(shaderJob,
        new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator(), false));

    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testTooLargeToInline() throws Exception {

    final StringBuilder program = new StringBuilder();
    program.append("int donotinlineme(int x) { int y = 2;");
    for (int i = 0; i < 100; i++) {
      program.append(" y = y + x;");
    }
    program.append("}");
    program.append("void main() { int z = donotinlineme(5); }");

    final TranslationUnit tu = ParseHelper.parse(program.toString());
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(tu);

    final List<InlineFunctionReductionOpportunity> ops =
        InlineFunctionReductionOpportunities.findOpportunities(shaderJob,
            new ReducerContext(true,
                ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator(), false));

    assertEquals(0, ops.size());
  }


}