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

import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PruneUniformsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testPruneOne() throws Exception {
    final String program = "#version 320 es\n"
          + "precision highp float;"
          + "uniform float a;"
          + "uniform float prune_b;"
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
          + "  \"prune_b\": {\n"
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
          + "float prune_b = 23.0;"
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

    List<String> prefixList = Collections.singletonList("prune");
    int limit = 2;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, prefixList, limit);

  }

  @Test
  public void testPruneAll() throws Exception {
    final String program = "#version 310 es\n"
          + "precision highp float;"
          + "uniform int liveI[10];"
          + "uniform vec3 deadF[3];"
          + "uniform vec2 liveG, deadH;"
          + "uniform uint liveA, liveB;"
          + "uniform bvec3 liveC[3];"
          + "uniform bool liveZ;"
          + "void main() {"
          + "}";
    final String uniforms = "{\n"
          + "  \"liveI\": {\n"
          + "    \"func\": \"glUniform1iv\",\n"
          + "    \"args\": [\n"
          + "      1, 2, 3, 4, 5, 6, 7, 8, 9, 10\n"
          + "    ]\n"
          + "  },\n"
          + "  \"deadF\": {\n"
          + "    \"func\": \"glUniform3fv\",\n"
          + "    \"args\": [\n"
          + "      1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"liveG\": {\n"
          + "    \"func\": \"glUniform2f\",\n"
          + "    \"args\": [\n"
          + "      256.0, 257.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"deadH\": {\n"
          + "    \"func\": \"glUniform2f\",\n"
          + "    \"args\": [\n"
          + "      258.0, 259.0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"liveA\": {\n"
          + "    \"func\": \"glUniform1ui\",\n"
          + "    \"args\": [\n"
          + "      25\n"
          + "    ]\n"
          + "  },\n"
          + "  \"liveB\": {\n"
          + "    \"func\": \"glUniform1ui\",\n"
          + "    \"args\": [\n"
          + "      26\n"
          + "    ]\n"
          + "  },\n"
          + "  \"liveC\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      0, 1, 0, 1, 0, 1, 0, 1, 0\n"
          + "    ]\n"
          + "  },\n"
          + "  \"liveZ\": {\n"
          + "    \"func\": \"glUniform1i\",\n"
          + "    \"args\": [\n"
          + "      1\n"
          + "    ]\n"
          + "  }\n"
          + "}\n";

    final String expectedProgram = "#version 310 es\n"
          + "precision highp float;"
          + "int liveI[10] = int[10](1, 2, 3, 4, 5, 6, 7, 8, 9, 10);"
          + "vec3 deadF[3] = vec3[3](vec3(1.0, 2.0, 3.0), vec3(4.0, 5.0, 6.0), vec3(7.0, 8.0, 9.0));"
          + "vec2 deadH = vec2(258.0, 259.0);"
          + "vec2 liveG = vec2(256.0, 257.0);"
          + "uint liveA = 25u;"
          + "uint liveB = 26u;"
          + "bvec3 liveC[3] = bvec3[3](bvec3(false, true, false), bvec3(true, false, true), "
                  + "bvec3(false, true, false));"
          + "bool liveZ = true;"
          + "void main() {"
          + "}";
    final String expectedUniforms = "{\n"
          + "}\n";

    List<String> prefixList = Arrays.asList("live", "dead");
    int limit = 0;

    doPruneTest(program, uniforms, expectedProgram, expectedUniforms, prefixList, limit);

  }

  private void doPruneTest(String program, String uniforms, String expectedProgram,
        String expectedUniforms, List<String> prefixList, int limit)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(uniforms),
        ParseHelper.parse(program));

    PruneUniforms.pruneIfNeeded(
        shaderJob,
        limit,
        prefixList);

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
