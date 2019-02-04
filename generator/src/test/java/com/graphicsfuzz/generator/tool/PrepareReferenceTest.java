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

import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

  @Test
  public void testPreparedComputeShaderNoBindingClash() throws Exception {

    final String json = "{\n"
        + "  \"f\": {\n"
        + "    \"func\": \"glUniform1f\",\n"
        + "    \"args\": [\n"
        + "      1.0\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String comp = "#version 310 es\n"
        + "layout(std430, binding = 0) buffer doesNotMatter {\n"
        + " int result;\n"
        + " int data[];\n"
        + "} ;\n"
        + "layout(local_size_x = 16, local_size_y = 1) in;\n"
        + "uniform float f;\n"
        + "void main() { }";

    final File jsonFile = temporaryFolder.newFile("shader.json");
    final File compFile = temporaryFolder.newFile("shader.comp");
    final File output = temporaryFolder.newFile("output.json");

    FileUtils.writeStringToFile(jsonFile, json, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(compFile, comp, StandardCharsets.UTF_8);

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    PrepareReference.mainHelper(new String[] { jsonFile.getAbsolutePath(),
        output.getAbsolutePath(), "--generate-uniform-bindings" });

    assertTrue(fileOps.areShadersValid(output, false));

    final PipelineInfo pipelineInfo = new PipelineInfo(output);
    assertTrue(pipelineInfo.hasUniform("f"));

    // Cannot be 0, since that was already taken by the ssbo.
    assertEquals(1, pipelineInfo.getBinding("f"));

  }

  @Test
  public void testPreparedComputeShaderNoBindingClash2() throws Exception {

    final String json = "{\n"
        + "  \"f\": {\n"
        + "    \"func\": \"glUniform1f\",\n"
        + "    \"args\": [\n"
        + "      1.0\n"
        + "    ]\n"
        + "  },\n"
        + "  \"g\": {\n"
        + "    \"func\": \"glUniform1f\",\n"
        + "    \"args\": [\n"
        + "      1.0\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String comp = "#version 310 es\n"
        + "layout(std430, binding = 1) buffer doesNotMatter {\n"
        + " int result;\n"
        + " int data[];\n"
        + "} ;\n"
        + "layout(local_size_x = 16, local_size_y = 1) in;\n"
        + "uniform float f;\n"
        + "uniform float g;\n"
        + "void main() { }";

    final File jsonFile = temporaryFolder.newFile("shader.json");
    final File compFile = temporaryFolder.newFile("shader.comp");
    final File output = temporaryFolder.newFile("output.json");

    FileUtils.writeStringToFile(jsonFile, json, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(compFile, comp, StandardCharsets.UTF_8);

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    PrepareReference.mainHelper(new String[] { jsonFile.getAbsolutePath(),
        output.getAbsolutePath(), "--generate-uniform-bindings" });

    assertTrue(fileOps.areShadersValid(output, false));

    final PipelineInfo pipelineInfo = new PipelineInfo(output);

    // Check that f and g get given distinct bindings, and that neither get binding 1 (taken by
    // the ssbo).
    assertTrue(pipelineInfo.hasUniform("f"));
    assertTrue(pipelineInfo.hasUniform("g"));
    assertNotEquals(pipelineInfo.getBinding("f"), pipelineInfo.getBinding("g"));
    assertTrue(Arrays.asList(0, 2).contains(pipelineInfo.getBinding("f")));
    assertTrue(Arrays.asList(0, 2).contains(pipelineInfo.getBinding("g")));

  }


}
