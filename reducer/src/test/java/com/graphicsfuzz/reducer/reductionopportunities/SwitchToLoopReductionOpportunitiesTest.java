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

package com.graphicsfuzz.reducer.reductionopportunities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.util.List;
import org.junit.Test;

public class SwitchToLoopReductionOpportunitiesTest {

  @Test
  public void testDoNotReduceIfPreservingSemantics() throws Exception {
    final String original = "void main() {\n"
        + " if (" + Constants.GLF_DEAD + "(false)) {\n"
        + "  switch (0) {\n"
        + "   case 0:\n"
        + "    return;\n"
        + "  }\n"
        + " }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops =
        VariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_320,
                new RandomWrapper(0), new IdGenerator()));
    // There should be no opportunities as the preserve semantics is enabled.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testDoReduceWhenPreservingSemanticsIfInDeadCode() throws Exception {
    final String original = "void main() {\n"
        + " if (" + Constants.GLF_DEAD + "(false)) {\n"
        + "  switch (0) {\n"
        + "   case 0:\n"
        + "    return;\n"
        + "  }\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + " if (" + Constants.GLF_DEAD + "(false)) {\n"
        + "  do {\n"
        + "   0;"
        + "   return;\n"
        + "  } while (false);\n"
        + " }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<SwitchToLoopReductionOpportunity> ops =
        SwitchToLoopReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_320,
                new RandomWrapper(0), new IdGenerator()));
    // There should be an opportunity, as the switch statement is in a dead code block.
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoBreaks() throws Exception {
    final String original = "void main() {\n"
        + " int a;\n"
        + " int x = 3;\n"
        + " switch (x) {\n"
        + "  case 0:\n"
        + "   {\n"
        + "    x = 1;\n"
        + "   }\n"
        + "  case 1:\n"
        + "   a = 4;\n"
        + "   x = 2;\n"
        + "  case 3:\n"
        + "  default:\n"
        + "   x = 1;\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + " int a;\n"
        + " int x = 3;\n"
        + " do {\n"
        + "  x;\n"
        + "  {\n"
        + "   x = 1;\n"
        + "  }\n"
        + "  a = 4;\n"
        + "  x = 2;\n"
        + "  x = 1;\n"
        + " } while (false);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<SwitchToLoopReductionOpportunity> ops =
        SwitchToLoopReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                true, ShadingLanguageVersion.ESSL_320,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testWithBreaks() throws Exception {
    final String original = "void main() {\n"
        + " int a;\n"
        + " int x = 3;\n"
        + " switch (x) {\n"
        + "  case 0:\n"
        + "   {\n"
        + "    x = 1;\n"
        + "   }\n"
        + "   break;\n"
        + "  case 1:\n"
        + "   a = 4;\n"
        + "   x = 2;\n"
        + "   break;\n"
        + "  case 3:\n"
        + "  default:\n"
        + "   x = 1;\n"
        + "   break;\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + " int a;\n"
        + " int x = 3;\n"
        + " do {\n"
        + "  x;\n"
        + "  {\n"
        + "   x = 1;\n"
        + "  }\n"
        + "  break;\n"
        + "  a = 4;\n"
        + "  x = 2;\n"
        + "  break;\n"
        + "  x = 1;\n"
        + "  break;\n"
        + " } while (false);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<SwitchToLoopReductionOpportunity> ops =
        SwitchToLoopReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                true, ShadingLanguageVersion.ESSL_320,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testTwoSwitches() throws Exception {
    final String original = "void main() {\n"
        + " int x;\n"
        + " switch (0) {\n"
        + "  case 0:\n"
        + "   x = 1;\n"
        + " }\n"
        + " switch (1) {\n"
        + "  case 1:\n"
        + "   x = 2;\n"
        + " }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + " int x;\n"
        + " do {\n"
        + "  0;\n"
        + "  x = 1;\n"
        + " } while (false);\n"
        + " do {\n"
        + "  1;\n"
        + "  x = 2;\n"
        + " } while (false);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<SwitchToLoopReductionOpportunity> ops =
        SwitchToLoopReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                true, ShadingLanguageVersion.ESSL_320,
                new RandomWrapper(0), new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
