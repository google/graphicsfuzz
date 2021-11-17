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
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class InlineUniformReductionOpportunitiesTest {

  @Test
  public void inlineUniforms() throws Exception {
    final String prog =
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (f > float(i + i)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    f + v.x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}";
    final TranslationUnit tu = ParseHelper.parse(prog);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("f", BasicType.FLOAT, Optional.empty(), Arrays.asList(3.2));
    pipelineInfo.addUniform("i", BasicType.INT, Optional.empty(), Arrays.asList(10));
    pipelineInfo.addUniform("j", BasicType.INT, Optional.empty(), Arrays.asList(20));
    pipelineInfo.addUniform("v", BasicType.VEC2, Optional.empty(), Arrays.asList(2.2, 2.3));
    pipelineInfo.addUniform("u", BasicType.UINT, Optional.empty(), Arrays.asList(17));

    ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), pipelineInfo, tu);

    shaderJob = checkCanReduceToTarget(shaderJob, 8, "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (f > float(10 + i)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    f + v.x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 7, "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (f > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    f + v.x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 6, "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    f + v.x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 5,
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    3.2 + v.x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 4,
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 3,
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(17u) + float(j);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 2,
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(17u) + float(20);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

    checkCanReduceToTarget(shaderJob, 1,
        "uniform float f;"
            + "uniform int i, j;"
            + "uniform vec2 v;"
            + "uniform uint u;"
            + "void main() {"
            + "  if (3.2 > float(10 + 10)) {"
            + "    int i = 2;" // Hides the uniform declaration of i
            + "    3.2 + vec2(2.2, 2.3).x + vec2(2.2, 2.3).y + float(i) + float(17u) + float(20);"
            + "    {"
            + "       uint u = 4u;" // Hides the uniform declaration of u
            + "       u += u;"
            + "    }"
            + "  }"
            + "}");

  }

  private ShaderJob checkCanReduceToTarget(ShaderJob shaderJob, int expectedSize, String target)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    for (int i = 0; i < expectedSize; i++) {
      final ShaderJob temp = shaderJob.clone();
      List<SimplifyExprReductionOpportunity> ops =
          InlineUniformReductionOpportunities.findOpportunities(temp,
              new ReducerContext(true, true,
                  ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null));
      assertEquals(expectedSize, ops.size());
      ops.get(i).applyReduction();
      if (CompareAsts.isEqualAsts(target, temp.getShaders().get(0))) {
        return temp;
      }
    }
    assertTrue(false);
    return null;
  }

  @Test
  public void testDoNotInlineWhenPreservingSemantics() throws Exception {
    final String shader = "#version 310 es\n"
        + "uniform int u;\n"
        + "void main() {\n"
        + "  u;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("u", BasicType.INT, Optional.empty(), Collections.singletonList(2));
    final List<SimplifyExprReductionOpportunity> opportunities =
        InlineUniformReductionOpportunities.findOpportunities(new GlslShaderJob(Optional.empty(),
            pipelineInfo, tu), new ReducerContext(false, true, ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0), new IdGenerator()));
    assertTrue(opportunities.isEmpty());
  }

  @Test
  public void testDoInlineWhenPreservingSemanticsDeadCode() throws Exception {
    final String shader = "#version 310 es\n"
        + "uniform int u;\n"
        + "void main() {\n"
        + "  u;\n"
        + "  if(" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false, false))) {\n"
        + "    u;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "uniform int u;\n"
        + "void main() {\n"
        + "  u;\n"
        + "  if(" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false, false))) {\n"
        + "    2;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("u", BasicType.INT, Optional.empty(), Collections.singletonList(2));
    final List<SimplifyExprReductionOpportunity> opportunities =
        InlineUniformReductionOpportunities.findOpportunities(new GlslShaderJob(Optional.empty(),
            pipelineInfo, tu), new ReducerContext(false, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoInlineWhenPreservingSemanticsDeadFunction() throws Exception {
    final String shader = "#version 310 es\n"
        + "uniform int u;\n"
        + "void " + Constants.GLF_DEAD + "_foo() {\n"
        + "  u;\n"
        + "}\n"
        + "void main() {\n"
        + "  u;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "uniform int u;\n"
        + "void " + Constants.GLF_DEAD + "_foo() {\n"
        + "  2;\n"
        + "}\n"
        + "void main() {\n"
        + "  u;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("u", BasicType.INT, Optional.empty(), Collections.singletonList(2));
    final List<SimplifyExprReductionOpportunity> opportunities =
        InlineUniformReductionOpportunities.findOpportunities(new GlslShaderJob(Optional.empty(),
            pipelineInfo, tu), new ReducerContext(false, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoInlineWhenPreservingSemanticsUnreachableSwitchCase() throws Exception {
    final String shader = "#version 310 es\n"
        + "uniform int u;\n"
        + "void main() {\n"
        + "  switch(" + Constants.GLF_SWITCH + "(0)) {\n"
        + "    case 2:\n"
        + "    u;\n"
        + "    case 3:\n"
        + "    u;\n"
        + "    case 0:\n"
        + "    u;\n"
        + "    case 5:\n"
        + "    u;\n"
        + "    break;\n"
        + "    case 6:\n"
        + "    u;\n"
        + "    default:\n"
        + "    u;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "uniform int u;\n"
        + "void main() {\n"
        + "  switch(" + Constants.GLF_SWITCH + "(0)) {\n"
        + "    case 2:\n"
        + "    2;\n"
        + "    case 3:\n"
        + "    2;\n"
        + "    case 0:\n"
        + "    u;\n"
        + "    case 5:\n"
        + "    u;\n"
        + "    break;\n"
        + "    case 6:\n"
        + "    2;\n"
        + "    default:\n"
        + "    2;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("u", BasicType.INT, Optional.empty(), Collections.singletonList(2));
    final List<SimplifyExprReductionOpportunity> opportunities =
        InlineUniformReductionOpportunities.findOpportunities(new GlslShaderJob(Optional.empty(),
            pipelineInfo, tu), new ReducerContext(false, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(4, opportunities.size());
    for (SimplifyExprReductionOpportunity op : opportunities) {
      op.applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoInlineWhenPreservingSemanticsLiveCode() throws Exception {
    final String shader = "#version 310 es\n"
        + "uniform int u;\n"
        + "uniform int " + Constants.LIVE_PREFIX + "v;\n"
        + "void main() {\n"
        + "  u;\n"
        + "  " + Constants.LIVE_PREFIX + "v;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "uniform int u;\n"
        + "uniform int " + Constants.LIVE_PREFIX + "v;\n"
        + "void main() {\n"
        + "  u;\n"
        + "  3;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("u", BasicType.INT, Optional.empty(), Collections.singletonList(2));
    pipelineInfo.addUniform(Constants.LIVE_PREFIX + "v", BasicType.INT, Optional.empty(),
        Collections.singletonList(3));
    final List<SimplifyExprReductionOpportunity> opportunities =
        InlineUniformReductionOpportunities.findOpportunities(new GlslShaderJob(Optional.empty(),
            pipelineInfo, tu), new ReducerContext(false, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
