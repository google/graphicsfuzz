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

import static org.junit.Assert.assertTrue;

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
        + "  gl_Position = _GLF_pos;\n"
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

  @Test
  public void shaderVersionTest() throws Exception {
    PipelineInfo pipelineInfo = new PipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();

    final String frag =  "#version 410\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  _GLF_color = vec4(1.0, 1.0, 1.0, 1.0);\n"
        + "}\n";

    final String vert = "#version 110\n"
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

    final String expectedVertexShader = "#version 410\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) in vec4 _GLF_pos;\n"
        + "\n"
        + "layout(location = 0) out vec4 frag_color;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " frag_color = vec4(1.0, 1.0, 1.0, 1.0);\n"
        + " gl_Position = _GLF_pos;\n"
        + "}";

    final String expectedFragmentShader = "#version 410\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "layout(location = 0) in vec4 frag_color;\n"
        + "\n"
        + "void main() {\n"
        + "  _GLF_color = frag_color;\n"
        + "}";

    CompareAsts.assertEqualAsts(expectedFragmentShader, result.getFragmentShader().get());
    CompareAsts.assertEqualAsts(expectedVertexShader, result.getVertexShader().get());
  }

  @Test
  public void fragmentOnlyFunctionsTest() throws Exception {
    PipelineInfo pipelineInfo = new PipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();

    final String frag =  "#version 430\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  vec2 lin = gl_FragCoord.xy / resolution;\n"
        + "  if (lin.x > lin.y) discard;\n"
        + "  gl_FragDepth = dFdx(0.01 + dFdy(0.02 + dFdxFine(0.03 + dFdyFine(0.04))));\n"
        + "  gl_FragDepth += dFdxCoarse(0.05 + dFdyCoarse(0.06 + fwidth(0.07)));\n"
        + "  gl_FragDepth += sin(fwidthFine(0.08) + sin(fwidthCoarse((0.09))));"
        + "  gl_FragDepth += interpolateAtCentroid(0.10) + interpolateAtOffset(0.11, sin(2.0));"
        + "  _GLF_color = vec4(1.0, 1.0, 1.0, 1.0);\n"
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
        + "float _GLF_FragDepth;\n"
        + "vec4 _GLF_FragCoord;\n"
        + "layout(location = 1) out bool _GLF_discard;\n"
        + "layout(location = 0) in vec4 _GLF_pos;\n"
        + "layout(location = 0) out vec4 frag_color;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  _GLF_discard = false;\n"
        + "  _GLF_FragCoord = (_GLF_pos + vec4(1.0,1.0,0.0,0.0)) * vec4(128.0, 128.0, 1.0, 1.0);\n"
        + "  vec2 lin = _GLF_FragCoord.xy / resolution;\n"
        + "  if (lin.x > lin.y) _GLF_discard = true;\n"
        + "  _GLF_FragDepth = (0.01 + (0.02 + (0.03 + (0.04))));\n"
        + "  _GLF_FragDepth += (0.05 + (0.06 + (0.07)));\n"
        + "  _GLF_FragDepth += sin((0.08) + sin(((0.09))));\n"
        + "  _GLF_FragDepth += (0.10) + (0.11);"
        + "  frag_color = vec4(1.0, 1.0, 1.0, 1.0);\n"
        + "  gl_Position = _GLF_pos;\n"
        + "}\n";

    final String expectedFragmentShader = "#version 430\n"
        + "precision highp float;\n"
        + "\n"
        + "layout(location = 1) in bool _GLF_discard;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "layout(location = 0) in vec4 frag_color;\n"
        + "\n"
        + "void main() {\n"
        + "  if (_GLF_discard) discard;\n"
        + "  _GLF_color = frag_color;\n"
        + "}";

    CompareAsts.assertEqualAsts(expectedFragmentShader, result.getFragmentShader().get());
    CompareAsts.assertEqualAsts(expectedVertexShader, result.getVertexShader().get());
  }

}
