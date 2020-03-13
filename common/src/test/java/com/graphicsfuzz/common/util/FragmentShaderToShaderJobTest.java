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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;

public class FragmentShaderToShaderJobTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void basicJsonGenerationTest() throws Exception {
    final String frag =  "#version 310 es\n"
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

    assertTrue(vertdata.contains("layout(location = 0) in vec4 " + Constants.GLF_POS));
    assertTrue(vertdata.contains("gl_Position = " + Constants.GLF_POS));

    assertTrue(fragdata.contains("layout(location = 1) in float in_f;"));
    assertTrue(fragdata.contains("layout(location = 2) in vec2 in_v2;"));
    assertTrue(fragdata.contains("layout(location = 3) in vec3 in_v3;"));
    assertTrue(fragdata.contains("layout(location = 4) in vec4 in_v4;"));

    assertTrue(fragdata.contains("layout(location = 0) out vec4 " + Constants.GLF_COLOR));
  }

}
