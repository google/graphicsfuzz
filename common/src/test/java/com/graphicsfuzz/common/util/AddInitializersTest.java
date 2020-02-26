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

import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.Optional;
import org.junit.Test;

public class AddInitializersTest {

  @Test
  public void basicTest() throws Exception {
    final String shader = "#version 310 es\n"
        + "precision highp float;\n"
        + "uniform float time;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "struct S {\n"
        + "  int a;\n"
        + "  float b[3];\n"
        + "} myS;\n"
        + "S anotherS;\n"
        + "const int g;\n"
        + "const int h = 2;\n"
        + "void main() {"
        + "  vec2 v;\n"
        + "  mat2x3 m;\n"
        + "  bvec4 b;\n"
        + "}\n";
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(),
        ParseHelper.parse(shader, ShaderKind.FRAGMENT));
    AddInitializers.addInitializers(shaderJob);

    final String expected = "#version 310 es\n"
        + "precision highp float;\n"
        + "uniform float time;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "struct S {\n"
        + "  int a;\n"
        + "  float b[3];\n"
        + "} myS = S(1, float[3](1.0, 1.0, 1.0));\n"
        + "S anotherS = S(1, float[3](1.0, 1.0, 1.0));\n"
        + "const int g = 1;\n"
        + "const int h = 2;\n"
        + "void main() {"
        + "  vec2 v = vec2(1.0);\n"
        + "  mat2x3 m = mat2x3(1.0);\n"
        + "  bvec4 b = bvec4(true);\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected, shaderJob.getFragmentShader().get());



  }

}
