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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PruneUniformsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testPruneOne() throws Exception {
    final String program = "#version 320 es\n"
          + "precision highp float;"
          + "uniform float a;"
          + "uniform float " + Constants.LIVE_PREFIX + "_b;"
          + "uniform int c;"
          + "void main() {"
          + "}";
    final String uniforms = "{\n"
          + "  \"a\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      256.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "_b\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      23.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"c\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      256\n"
          + "    ]\n"
          + "  }\n"
          + "}\n";

    final String expectedProgram = "#version 320 es\n"
          + "precision highp float;"
          + "uniform float a;"
          + "float " + Constants.LIVE_PREFIX + "_b = 23.0;"
          + "uniform int c;"
          + "void main() {"
          + "}";
    final String expectedUniforms = "{\n"
          + "  \"a\": {\n"
          + "    \"func\": \"glUniform1f\",\n"
          + "    \"args\": [\n"
          + "      256.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"c\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      256\n"
          + "    ]\n"
          + "  }\n"
          + "}\n";

    int limit = 2;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);

  }

  @Test
  public void testPruneAll() throws Exception {
    final String program = "#version 310 es\n"
          + "precision highp float;"
          + "uniform int " + Constants.LIVE_PREFIX + "I[10];"
          + "uniform vec3 " + Constants.DEAD_PREFIX + "F[3];"
          + "uniform vec2 " + Constants.LIVE_PREFIX + "G, " + Constants.DEAD_PREFIX + "H;"
          + "uniform uint " + Constants.LIVE_PREFIX + "A, " + Constants.LIVE_PREFIX + "B;"
          + "uniform bvec3 " + Constants.LIVE_PREFIX + "C[3];"
          + "uniform bool " + Constants.LIVE_PREFIX + "Z;"
          + "void main() {"
          + "}";
    final String uniforms = "{\n"
          + "  \"" + Constants.LIVE_PREFIX + "I\": {\n"
          + "    \"func\": \"glUniform1iv\",\n"
          + "    \"args\": [\n"
          + "      1, 2, 3, 4, 5, 6, 7, 8, 9, 10\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.DEAD_PREFIX + "F\": {\n"
          + "    \"func\": \"glUniform3fv\",\n"
          + "    \"args\": [\n"
          + "      1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "G\": {\n"
          + "    \"func\": \"glUniform2f\",\n"
          + "    \"args\": [\n"
          + "      256.0, 257.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.DEAD_PREFIX + "H\": {\n"
          + "    \"func\": \"glUniform2f\",\n"
          + "    \"args\": [\n"
          + "      258.0, 259.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "A\": {\n"
          + "    \"func\": \"glUniform1ui\",\n"
          + "    \"args\": [\n"
          + "      25\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "B\": {\n"
          + "    \"func\": \"glUniform1ui\",\n"
          + "    \"args\": [\n"
          + "      26\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "C\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      0, 1, 0, 1, 0, 1, 0, 1, 0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"" + Constants.LIVE_PREFIX + "Z\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      1\n"
          + "    ]\n"
          + "  }\n"
          + "}\n";

    final String expectedProgram = "#version 310 es\n"
          + "precision highp float;"
          + "int " + Constants.LIVE_PREFIX + "I[10] = int[10](1, 2, 3, 4, 5, 6, 7, 8, 9, 10);"
          + "vec3 " + Constants.DEAD_PREFIX + "F[3] = vec3[3](vec3(1.0, 2.0, 3.0), "
                  + "vec3(4.0, 5.0, 6.0), vec3(7.0, 8.0, 9.0));"
          + "vec2 " + Constants.DEAD_PREFIX + "H = vec2(258.0, 259.0);"
          + "vec2 " + Constants.LIVE_PREFIX + "G = vec2(256.0, 257.0);"
          + "uint " + Constants.LIVE_PREFIX + "A = 25u;"
          + "uint " + Constants.LIVE_PREFIX + "B = 26u;"
          + "bvec3 " + Constants.LIVE_PREFIX + "C[3] = bvec3[3](bvec3(false, true, false), "
                  + "bvec3(true, false, true), bvec3(false, true, false));"
          + "bool " + Constants.LIVE_PREFIX + "Z = true;"
          + "void main() {"
          + "}";
    final String expectedUniforms = "{\n"
          + "}\n";

    int limit = 0;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);

  }

  @Test
  public void doNotPruneReferenceShaderSampler() throws Exception {
    final String program = "#version 310 es\n"
        + "uniform sampler2D tex;"
        + "uniform int a;"
        + "void main() {"
        + "}";
    final String uniforms = "{\n"
        + "  \"a\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      1\n"
        + "    ]\n"
        + "  },\n"
        + "  \"tex\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    final String expectedProgram = "#version 310 es\n"
        + "uniform sampler2D tex;"
        + "int a = 1;"
        + "void main() {"
        + "}";
    final String expectedUniforms = "{\n"
        + "  \"tex\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    int limit = 1;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);

  }

  @Test
  public void mergeVariantShaderSamplersWithReferenceShaderSampler() throws Exception {
    final String live_tex = Constants.LIVE_PREFIX + "tex";
    final String dead_tex = Constants.DEAD_PREFIX + "tex";
    final String program = "#version 310 es\n"
        + "uniform sampler2D " + live_tex + ";"
        + "uniform sampler2D " + dead_tex + ";"
        + "void foo() {"
        + "  texture(" + live_tex + ", vec2(1.0));"
        + "  texture(" + dead_tex + ", vec2(2.0));"
        + "}"
        + "uniform sampler2D tex, tex2;"
        + "void main() {"
        + "  foo();"
        + "  texture(tex, vec2(0.0));"
        + "  texture(tex2, vec2(100.0));"
        + "}";
    final String uniforms = "{\n"
        + "  \"tex\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  },\n"
        + "  \"" + live_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  },\n"
        + "  \"" + dead_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    final String expectedProgram = "#version 310 es\n"
        + "uniform sampler2D tex;"
        + "void foo() {"
        + "  texture(tex, vec2(1.0));"
        + "  texture(tex, vec2(2.0));"
        + "}"
        + "uniform sampler2D tex2;"
        + "void main() {"
        + "  foo();"
        + "  texture(tex, vec2(0.0));"
        + "  texture(tex2, vec2(100.0));"
        + "}";
    final String expectedUniforms = "{\n"
        + "  \"tex\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    int limit = 1;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);
  }

  @Test
  public void mergeDonatedSamplers() throws Exception {
    final String live_tex = Constants.LIVE_PREFIX + "tex";
    final String live_tex2 = Constants.LIVE_PREFIX + "tex2";
    final String dead_tex = Constants.DEAD_PREFIX + "tex";
    final String program = "#version 310 es\n"
        + "uniform sampler2D " + live_tex + ", " + dead_tex + ";"
        + "void foo() {"
        + "  texture(" + live_tex + ", vec2(1.0));"
        + "  texture(" + dead_tex + ", vec2(2.0));"
        + "}"
        + "uniform sampler2D " + live_tex2 + ";"
        + "void main() {"
        + "  foo();"
        + "  texture(" + dead_tex + ", vec2(0.0));"
        + "  texture(" + live_tex2 + ", vec2(100.0));"
        + "}";
    final String uniforms = "{\n"
        + "  \"" + dead_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  },\n"
        + "  \"" + live_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  },\n"
        + "  \"" + live_tex2 + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    final String expectedProgram = "#version 310 es\n"
        + "uniform sampler2D " + dead_tex + ";"
        + "void foo() {"
        + "  texture(" + dead_tex + ", vec2(1.0));"
        + "  texture(" + dead_tex + ", vec2(2.0));"
        + "}"
        + "void main() {"
        + "  foo();"
        + "  texture(" + dead_tex + ", vec2(0.0));"
        + "  texture(" + dead_tex + ", vec2(100.0));"
        + "}";
    final String expectedUniforms = "{\n"
        + "  \"" + dead_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    int limit = 1;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);
  }

  @Test
  public void doNotPruneVariantShaderSamplerDueToNoOtherSampler() throws Exception {
    final String live_a = Constants.LIVE_PREFIX + "a";
    final String live_b = Constants.LIVE_PREFIX + "b";
    final String live_c = Constants.LIVE_PREFIX + "c";
    final String live_x = Constants.LIVE_PREFIX + "x";
    final String live_y = Constants.LIVE_PREFIX + "y";
    final String live_z = Constants.LIVE_PREFIX + "z";
    final String live_tex = Constants.LIVE_PREFIX + "tex";
    final String program = "#version 310 es\n"
        + "uniform int " + live_a + ";"
        + "uniform int " + live_b + ";"
        + "uniform int " + live_c + ";"
        + "uniform sampler2D " + live_tex + ";"
        + "uniform int " + live_x + ";"
        + "uniform int " + live_y + ";"
        + "uniform int " + live_z + ";"
        + "void main() {"
        + "  texture(" + live_tex + ", vec2(0.0));"
        + "  " + live_a + ";"
        + "  " + live_b + ";"
        + "  " + live_c + ";"
        + "  " + live_x + ";"
        + "  " + live_y + ";"
        + "  " + live_z + ";"
        + "}";
    final String uniforms = "{\n"
        + "  \"" + live_a + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      1\n"
        + "    ]\n"
        + "  },\n"
        + "  \"" + live_b + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      2\n"
        + "    ]\n"
        + "  },\n"
        + "  \"" + live_c + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      3\n"
        + "    ]\n"
        + "  },\n"
        + "  \"" + live_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  },\n"
        + "  \"" + live_x + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      4\n"
        + "    ]\n"
        + "  },\n"
        + "  \"" + live_y + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      5\n"
        + "    ]\n"
        + "  },\n"
        + "  \"" + live_z + "\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      6\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";

    final String expectedProgram = "#version 310 es\n"
        + "int " + live_a + " = 1;"
        + "int " + live_b + " = 2;"
        + "int " + live_c + " = 3;"
        + "uniform sampler2D " + live_tex + ";"
        + "int " + live_x + " = 4;"
        + "int " + live_y + " = 5;"
        + "int " + live_z + " = 6;"
        + "void main() {"
        + "  texture(" + live_tex + ", vec2(0.0));"
        + "  " + live_a + ";"
        + "  " + live_b + ";"
        + "  " + live_c + ";"
        + "  " + live_x + ";"
        + "  " + live_y + ";"
        + "  " + live_z + ";"
        + "}";
    final String expectedUniforms = "{\n"
        + "  \"" + live_tex + "\": {\n"
        + "    \"func\": \"sampler2D\",\n"
        + "    \"texture\": \"DEFAULT\"\n"
        + "  }\n"
        + "}\n";

    int limit = 1;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, limit);
  }

  private void doPruneTest(String program, String uniforms, String expectedProgram,
        String expectedUniforms, int limit)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(uniforms),
        ParseHelper.parse(program));

    PruneUniforms.pruneIfNeeded(
        shaderJob,
        limit);

    final File shaderJobFile = temporaryFolder.newFile("shader.json");

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    fileOps.writeShaderJobFile(shaderJob, shaderJobFile);
    assertTrue(fileOps.areShadersValid(shaderJobFile, false));

    final File expectedUniformsFile = temporaryFolder.newFile("expecteduniforms.json");
    FileUtils.writeStringToFile(expectedUniformsFile, expectedUniforms, StandardCharsets.UTF_8);

    CompareAsts.assertEqualAsts(expectedProgram, shaderJob.getFragmentShader().get());
    assertEquals(new PipelineInfo(expectedUniforms).toString(),
          shaderJob.getPipelineInfo().toString());
  }

}
