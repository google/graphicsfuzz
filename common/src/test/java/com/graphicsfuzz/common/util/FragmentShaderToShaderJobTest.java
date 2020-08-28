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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FragmentShaderToShaderJobTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void basicJsonGenerationTest() throws Exception {
    final String frag =  "#version 320 es\n"
        + "precision mediump float;\n"
        + "\n"
        + "layout(location = 0) out vec4 " + Constants.GLF_COLOR + ";\n"
        + "\n"
        + "uniform float u_f;\n"
        + "uniform vec2 u_v2;\n"
        + "uniform vec3 u_v3;\n"
        + "uniform vec4 u_v4;\n"
        + "\n"
        + "in float in_f;\n"
        + "in vec2 in_v2;\n"
        + "layout(location = 78) in vec3 in_v3;\n" // 78 expected to change to smaller value
        + "in vec4 in_v4;\n"
        + "\n"
        + "in float in_a, in_b, in_c;\n"
        + "in mat4 in_mat4;\n"
        + "in mat3 in_mat3;\n"
        + "in mat2 in_mat2;\n"
        + "in mat2x3 in_mat2x3;\n"
        + "in mat2x4 in_mat2x4;\n"
        + "in mat3x2 in_mat3x2;\n"
        + "in mat3x4 in_mat3x4;\n"
        + "in mat4x2 in_mat4x2;\n"
        + "in mat4x3 in_mat4x3;\n"
        + "\n"
        + "in bool in_bool;\n"
        + "in bvec2 in_bvec2;\n"
        + "in bvec3 in_bvec3;\n"
        + "in bvec4 in_bvec4;\n"
        + "in int in_int;\n"
        + "in ivec2 in_ivec2;\n"
        + "in ivec3 in_ivec3;\n"
        + "in ivec4 in_ivec4;\n"
        + "in uint in_uint;\n"
        + "in uvec2 in_uvec2;\n"
        + "in uvec3 in_uvec3;\n"
        + "in uvec4 in_uvec4;\n"
        + "\n"
        + "void main(void)\n"
        + "{\n"
        + "  " + Constants.GLF_COLOR + " = vec4(0.0, 0.0, 0.0, 1.0);\n"
        + "}\n";

    final File jsonFile = temporaryFolder.newFile("shader.json");
    final File vertFile = temporaryFolder.newFile("shader.vert");
    final File fragFile = temporaryFolder.newFile("shader.frag");

    final TranslationUnit tu = ParseHelper.parse(frag);
    final ShaderJob result = FragmentShaderToShaderJob.createShaderJob(tu,
        new RandomWrapper(0));

    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    fileOperations.writeShaderJobFile(result, jsonFile);

    /* The result files contain random values so we can't do a
       simple 1:1 comparison. So, check if the files exist,
       read the contents and check that the files contain the
       strings we expect to be in them.
     */

    assertTrue(jsonFile.isFile());
    assertTrue(vertFile.isFile());
    assertTrue(fragFile.isFile());
    final String jsondata = fileOperations.readFileToString(jsonFile);
    final String vertdata = fileOperations.readFileToString(vertFile);
    final String fragdata = fileOperations.readFileToString(fragFile);

    assertTrue(jsondata.contains("\"u_f\""));
    assertTrue(jsondata.contains("\"u_v2\""));
    assertTrue(jsondata.contains("\"u_v3\""));
    assertTrue(jsondata.contains("\"u_v4\""));
    assertTrue(jsondata.contains("\"glUniform1f\""));
    assertTrue(jsondata.contains("\"glUniform2f\""));
    assertTrue(jsondata.contains("\"glUniform3f\""));
    assertTrue(jsondata.contains("\"glUniform4f\""));

    assertTrue(vertdata.contains("in_f = "));
    assertTrue(vertdata.contains("in_v2 = vec2("));
    assertTrue(vertdata.contains("in_v3 = vec3("));
    assertTrue(vertdata.contains("in_v4 = vec4("));

    assertTrue(vertdata.contains("layout(location = 1) out float in_f;"));
    assertTrue(vertdata.contains("layout(location = 2) out vec2 in_v2;"));
    assertTrue(vertdata.contains("layout(location = 3) out vec3 in_v3;"));
    assertTrue(vertdata.contains("layout(location = 4) out vec4 in_v4;"));
    assertTrue(vertdata.contains("layout(location = 5) out float in_a;"));
    assertTrue(vertdata.contains("layout(location = 6) out float in_b;"));
    assertTrue(vertdata.contains("layout(location = 7) out float in_c;"));
    assertTrue(vertdata.contains("layout(location = 8) out mat4 in_mat4;"));
    assertTrue(vertdata.contains("layout(location = 12) out mat3 in_mat3;"));
    assertTrue(vertdata.contains("layout(location = 16) out mat2 in_mat2;"));
    assertTrue(vertdata.contains("layout(location = 20) out mat2x3 in_mat2x3;"));
    assertTrue(vertdata.contains("layout(location = 24) out mat2x4 in_mat2x4;"));
    assertTrue(vertdata.contains("layout(location = 28) out mat3x2 in_mat3x2;"));
    assertTrue(vertdata.contains("layout(location = 32) out mat3x4 in_mat3x4;"));
    assertTrue(vertdata.contains("layout(location = 36) out mat4x2 in_mat4x2;"));
    assertTrue(vertdata.contains("layout(location = 40) out mat4x3 in_mat4x3;"));

    assertTrue(vertdata.contains("layout(location = 44) out bool in_bool;"));
    assertTrue(vertdata.contains("layout(location = 45) out bvec2 in_bvec2;"));
    assertTrue(vertdata.contains("layout(location = 46) out bvec3 in_bvec3;"));
    assertTrue(vertdata.contains("layout(location = 47) out bvec4 in_bvec4;"));
    assertTrue(vertdata.contains("layout(location = 48) out int in_int;"));
    assertTrue(vertdata.contains("layout(location = 49) out ivec2 in_ivec2;"));
    assertTrue(vertdata.contains("layout(location = 50) out ivec3 in_ivec3;"));
    assertTrue(vertdata.contains("layout(location = 51) out ivec4 in_ivec4;"));
    assertTrue(vertdata.contains("layout(location = 52) out uint in_uint;"));
    assertTrue(vertdata.contains("layout(location = 53) out uvec2 in_uvec2;"));
    assertTrue(vertdata.contains("layout(location = 54) out uvec3 in_uvec3;"));
    assertTrue(vertdata.contains("layout(location = 55) out uvec4 in_uvec4;"));

    assertTrue(vertdata.contains("layout(location = 0) in vec4 " + Constants.GLF_POS));
    assertTrue(vertdata.contains("gl_Position = " + Constants.GLF_POS));

    assertTrue(fragdata.contains("layout(location = 1) in float in_f;"));
    assertTrue(fragdata.contains("layout(location = 2) in vec2 in_v2;"));
    assertTrue(fragdata.contains("layout(location = 3) in vec3 in_v3;"));
    assertTrue(fragdata.contains("layout(location = 4) in vec4 in_v4;"));

    assertTrue(fragdata.contains("layout(location = 5) in float in_a;"));
    assertTrue(fragdata.contains("layout(location = 6) in float in_b;"));
    assertTrue(fragdata.contains("layout(location = 7) in float in_c;"));
    assertTrue(fragdata.contains("layout(location = 8) in mat4 in_mat4;"));
    assertTrue(fragdata.contains("layout(location = 12) in mat3 in_mat3;"));
    assertTrue(fragdata.contains("layout(location = 16) in mat2 in_mat2;"));
    assertTrue(fragdata.contains("layout(location = 20) in mat2x3 in_mat2x3;"));
    assertTrue(fragdata.contains("layout(location = 24) in mat2x4 in_mat2x4;"));
    assertTrue(fragdata.contains("layout(location = 28) in mat3x2 in_mat3x2;"));
    assertTrue(fragdata.contains("layout(location = 32) in mat3x4 in_mat3x4;"));
    assertTrue(fragdata.contains("layout(location = 36) in mat4x2 in_mat4x2;"));
    assertTrue(fragdata.contains("layout(location = 40) in mat4x3 in_mat4x3;"));

    assertTrue(fragdata.contains("layout(location = 44) in bool in_bool;"));
    assertTrue(fragdata.contains("layout(location = 45) in bvec2 in_bvec2;"));
    assertTrue(fragdata.contains("layout(location = 46) in bvec3 in_bvec3;"));
    assertTrue(fragdata.contains("layout(location = 47) in bvec4 in_bvec4;"));
    assertTrue(fragdata.contains("layout(location = 48) in int in_int;"));
    assertTrue(fragdata.contains("layout(location = 49) in ivec2 in_ivec2;"));
    assertTrue(fragdata.contains("layout(location = 50) in ivec3 in_ivec3;"));
    assertTrue(fragdata.contains("layout(location = 51) in ivec4 in_ivec4;"));
    assertTrue(fragdata.contains("layout(location = 52) in uint in_uint;"));
    assertTrue(fragdata.contains("layout(location = 53) in uvec2 in_uvec2;"));
    assertTrue(fragdata.contains("layout(location = 54) in uvec3 in_uvec3;"));
    assertTrue(fragdata.contains("layout(location = 55) in uvec4 in_uvec4;"));

    assertTrue(fragdata.contains("layout(location = 0) out vec4 " + Constants.GLF_COLOR));
  }

}
