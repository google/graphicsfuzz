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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class PrepareReferenceTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testPreparedReferenceIsValid() throws Exception {

    final String json = "{ }";
    final String vert = "#version 300 es\n"
        + "layout(location=0) in highp vec4 a_position;\n"
        + "void main() { gl_Position = a_position; }";
    final String frag = "#version 300 es\n"
        + "layout(location=0) out highp vec4 _GLF_color;\n"
        + "void main() { _GLF_color = vec4(1.0, 0.0, 0.0, 1.0); }";

    final File jsonFile = temporaryFolder.newFile("shader.json");
    final File vertFile = temporaryFolder.newFile("shader.vert");
    final File fragFile = temporaryFolder.newFile("shader.frag");

    final File output = temporaryFolder.newFile("output.json");

    FileUtils.writeStringToFile(jsonFile, json, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(vertFile, vert, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(fragFile, frag, StandardCharsets.UTF_8);

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    PrepareReference.mainHelper(new String[] { jsonFile.getAbsolutePath(),
        output.getAbsolutePath() });


    assertTrue(fileOps.areShadersValid(output, false));

  }

}
