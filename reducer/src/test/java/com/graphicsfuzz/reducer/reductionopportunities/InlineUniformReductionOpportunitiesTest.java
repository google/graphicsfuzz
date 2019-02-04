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
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InlineUniformReductionOpportunitiesTest {

  @Test
  public void inlineUniforms() throws Exception {
    final String prog =
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (f > float(i + i)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    f + v.x + v.y + float(i) + float(u) + float(j);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}";
    final TranslationUnit tu = ParseHelper.parse(prog);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("f", BasicType.FLOAT, Optional.empty(), Arrays.asList(3.2));
    pipelineInfo.addUniform("i", BasicType.INT, Optional.empty(), Arrays.asList(10));
    pipelineInfo.addUniform("j", BasicType.INT, Optional.empty(), Arrays.asList(20));
    pipelineInfo.addUniform("v", BasicType.VEC2, Optional.empty(), Arrays.asList(2.2, 2.3));
    pipelineInfo.addUniform("u", BasicType.UINT, Optional.empty(), Arrays.asList(17));

    ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), pipelineInfo, tu);

    shaderJob = checkCanReduceToTarget(shaderJob, 8,
        "uniform float f;" +
        "uniform int i, j;" +
        "uniform vec2 v;" +
        "uniform uint u;" +
        "void main() {" +
        "  if (f > float(10 + i)) {" +
        "    int i = 2;" + // Hides the uniform declaration of i
        "    f + v.x + v.y + float(i) + float(u) + float(j);" +
        "    {" +
        "       uint u = 4u;" + // Hides the uniform declaration of u
        "       u += u;" +
        "    }" +
        "  }" +
        "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 7,
        "uniform float f;" +
        "uniform int i, j;" +
        "uniform vec2 v;" +
        "uniform uint u;" +
        "void main() {" +
        "  if (f > float(10 + 10)) {" +
        "    int i = 2;" + // Hides the uniform declaration of i
        "    f + v.x + v.y + float(i) + float(u) + float(j);" +
        "    {" +
        "       uint u = 4u;" + // Hides the uniform declaration of u
        "       u += u;" +
        "    }" +
        "  }" +
        "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 6,
        "uniform float f;" +
        "uniform int i, j;" +
        "uniform vec2 v;" +
        "uniform uint u;" +
        "void main() {" +
        "  if (3.2 > float(10 + 10)) {" +
        "    int i = 2;" + // Hides the uniform declaration of i
        "    f + v.x + v.y + float(i) + float(u) + float(j);" +
        "    {" +
        "       uint u = 4u;" + // Hides the uniform declaration of u
        "       u += u;" +
        "    }" +
        "  }" +
        "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 5,
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (3.2 > float(10 + 10)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    3.2 + v.x + v.y + float(i) + float(u) + float(j);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 4,
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (3.2 > float(10 + 10)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(u) + float(j);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 3,
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (3.2 > float(10 + 10)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(17u) + float(j);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}");

    shaderJob = checkCanReduceToTarget(shaderJob, 2,
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (3.2 > float(10 + 10)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    3.2 + vec2(2.2, 2.3).x + v.y + float(i) + float(17u) + float(20);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}");

    checkCanReduceToTarget(shaderJob, 1,
        "uniform float f;" +
            "uniform int i, j;" +
            "uniform vec2 v;" +
            "uniform uint u;" +
            "void main() {" +
            "  if (3.2 > float(10 + 10)) {" +
            "    int i = 2;" + // Hides the uniform declaration of i
            "    3.2 + vec2(2.2, 2.3).x + vec2(2.2, 2.3).y + float(i) + float(17u) + float(20);" +
            "    {" +
            "       uint u = 4u;" + // Hides the uniform declaration of u
            "       u += u;" +
            "    }" +
            "  }" +
            "}");

  }

  private ShaderJob checkCanReduceToTarget(ShaderJob shaderJob, int expectedSize, String target)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    boolean found = false;
    for (int i = 0; i < expectedSize; i++) {
      final ShaderJob temp = shaderJob.clone();
      List<SimplifyExprReductionOpportunity> ops =
          InlineUniformReductionOpportunities.findOpportunities(temp,
              new ReducerContext(false,
                  ShadingLanguageVersion.ESSL_100, new RandomWrapper(), null, true));
      assertEquals(expectedSize, ops.size());
      ops.get(i).applyReduction();
      if (CompareAsts.isEqualAsts(target, temp.getShaders().get(0))) {
        return temp;
      }
    }
    assertTrue(false);
    return null;
  }

}
