/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertTrue;

public class FragmentShaderJobToVertexShaderJobTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void basicVertexShaderGenerationTest() throws Exception {
    PipelineInfo pipelineInfo = new PipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();

    final String frag =  "#version 430\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "uniform vec2 resolution;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  vec2 lin = gl_FragCoord.xy / resolution;\n"
        + "  _GLF_color = vec4(lin.x,lin.y,0.0,1.0);\n"
        + "}\n";

    final String vert = "#version 430\n"
        + "\n"
        + "layout(location = 0) in vec4 position;\n"
        + "layout(location = 1) flat out vec4 frag_color;\n"
        + "\n"
        + "void main() {\n"
        + "  gl_Position = position;\n"
        + "  frag_color = position;\n"
        + "}\n";

    shaders.add(ParseHelper.parse(frag, ShaderKind.FRAGMENT));
    shaders.add(ParseHelper.parse(vert, ShaderKind.VERTEX));
    pipelineInfo.addUniform("resolution", BasicType.VEC2, Optional.empty(),
        Arrays.asList(256.0, 256.0));
    final ShaderJob input = new GlslShaderJob(Optional.empty(), pipelineInfo, shaders);
    final ShaderJob result = FragmentShaderJobToVertexShaderJob.convertShaderJob(input);

    final String expectedVertexShader = "#version 430\n"
        + "precision highp float;\n"
        + "\n"
        + "vec4 _GLF_FragCoord;\n"
        + "layout(location = 0) in vec4 _GLF_pos;\n"
        + "layout(location = 0) out vec4 frag_color;\n"
        + "uniform vec2 resolution;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  _GLF_FragCoord = (_GLF_pos + vec4(1.0,1.0,0.0,0.0)) * vec4(128.0, 128.0, 1.0, 1.0);\n"
        + "  vec2 lin = _GLF_FragCoord.xy / resolution;\n"
        + "  frag_color = vec4(lin.x,lin.y,0.0,1.0);\n"
        + "}\n";

    final String expectedFragmentShader = "#version 430\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "layout(location = 0) in vec4 frag_color;\n"
        + "\n"
        + "void main() {\n"
        + "  _GLF_color = frag_color;\n"
        + "}";

    assertTrue(result.getPipelineInfo().hasGridInfo());
    assertTrue(result.getPipelineInfo().getGridColumns() == 256);
    assertTrue(result.getPipelineInfo().getGridRows() == 256);
    CompareAsts.assertEqualAsts(expectedFragmentShader, result.getFragmentShader().get());
    CompareAsts.assertEqualAsts(expectedVertexShader, result.getVertexShader().get());
  }

}
