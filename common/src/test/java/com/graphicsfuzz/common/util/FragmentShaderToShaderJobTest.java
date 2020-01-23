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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
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
    + "layout(location = 0) out vec4 _GLF_color;\n"
    + "\n"
    + "uniform float u_f;\n"
    + "uniform vec2 u_v2;\n"
    + "uniform vec3 u_v3;\n"
    + "uniform vec4 u_v4;\n"
    + "\n"
    + "in float in_f;\n"
    + "in vec2 in_v2;\n"
    + "in vec3 in_v3;\n"
    + "in vec4 in_v4;\n"
    + "\n"
    + "void main(void)\n"
    + "{\n"
    + "  GLF_color = vec4(0.0,0.0,0.0,1.0);\n"
    + "}\n";

    final File fragFile = temporaryFolder.newFile("shader.frag");
    final File jsonFile = temporaryFolder.newFile("shader.json");
    final File vertFile = temporaryFolder.newFile("shader.vert");

    FileUtils.writeStringToFile(fragFile, frag, StandardCharsets.UTF_8);

    final String[] params = {fragFile.getAbsolutePath(), jsonFile.getAbsolutePath()};
    FragmentShaderToShaderJob.main(params);

    /* The result files contain random values so we can't do a
       simple 1:1 comparison. So, check if the files exist,
       read the contents and check that the files contain the
       strings we expect to be in them.
     */

    assertTrue(jsonFile.isFile());
    assertTrue(vertFile.isFile());

    final String jsondata = new String(Files.readAllBytes(Paths.get(jsonFile.getAbsolutePath())));
    final String vertdata = new String(Files.readAllBytes(Paths.get(vertFile.getAbsolutePath())));

    assertTrue(jsondata.indexOf("\"u_f\"") > -1);
    assertTrue(jsondata.indexOf("\"u_v2\"") > -1);
    assertTrue(jsondata.indexOf("\"u_v3\"") > -1);
    assertTrue(jsondata.indexOf("\"u_v4\"") > -1);
    assertTrue(jsondata.indexOf("\"glUniform1f\"") > -1);
    assertTrue(jsondata.indexOf("\"glUniform2f\"") > -1);
    assertTrue(jsondata.indexOf("\"glUniform3f\"") > -1);
    assertTrue(jsondata.indexOf("\"glUniform4f\"") > -1);
    
    assertTrue(vertdata.indexOf("in_f = ") > -1);
    assertTrue(vertdata.indexOf("in_v2 = vec2(") > -1);
    assertTrue(vertdata.indexOf("in_v3 = vec3(") > -1);
    assertTrue(vertdata.indexOf("in_v4 = vec4(") > -1);
    assertTrue(vertdata.indexOf("out float in_f;") > -1);
    assertTrue(vertdata.indexOf("out vec2 in_v2;") > -1);
    assertTrue(vertdata.indexOf("out vec3 in_v3;") > -1);
    assertTrue(vertdata.indexOf("out vec4 in_v4;") > -1);
  }

}
