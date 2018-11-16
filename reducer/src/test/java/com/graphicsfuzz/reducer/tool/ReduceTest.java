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

package com.graphicsfuzz.reducer.tool;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReduceTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void noServerAllowedInCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{"--server", "some_server", "--token", "some_token",
              makeShaderJobAndReturnJsonFilename(), "CUSTOM", "--output",
              temporaryFolder.getRoot().getAbsolutePath()}
          , null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "server");
    }
  }

  @Test
  public void noTokenAllowedInCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{"--token", "some_token",
          makeShaderJobAndReturnJsonFilename(), "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "token");
    }
  }

  @Test
  public void noErrorStringAllowedInCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{"--error_string", "some_string",
          makeShaderJobAndReturnJsonFilename(), "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "error_string");
    }
  }

  @Test
  public void noReferenceAllowedInCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{"--reference", "reference.info.json",
          makeShaderJobAndReturnJsonFilename(),
          "CUSTOM", "--output", temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "reference");
    }
  }

  private void checkOptionNotAllowed(RuntimeException exception, String option) {
    assertTrue(exception.getMessage().contains(option + "' option is not compatible"));
  }

  @Test
  public void noCustomJudgeAllowedInNonCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{makeShaderJobAndReturnJsonFilename(), "NO_IMAGE",
          "--custom_judge", "somejudgescript", "--output",
              temporaryFolder.getRoot().getAbsolutePath()},
          null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("custom_judge' option only supported with " +
          "CUSTOM reduction"));
    }
  }

  @Test
  public void customJudgeRequiredInCustomReduction() throws Exception {
    try {
      Reduce.mainHelper(new String[]{makeShaderJobAndReturnJsonFilename(), "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("CUSTOM reduction requires a judge " +
          "to be specified via '--custom_judge'"));
    }

  }

  @Test
  public void checkCustomJudgeIsExecutable() throws Exception {
    final File jsonFile = getShaderJobReady();
    final File emptyFile = temporaryFolder.newFile(); // Not executable
    try {
      Reduce.mainHelper(new String[]{
          jsonFile.getAbsolutePath(),
          "CUSTOM",
          "--custom_judge",
          emptyFile.getAbsolutePath(),
          "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue("An exception should have been thrown as the " +
          "judge script is not executable.", false);
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("judge script must be executable"));
    }
  }

  @Test
  public void testAlwaysReduceJudgeMaximallyReduces() throws Exception {
    final File jsonFile = getShaderJobReady();
    final File emptyFile = temporaryFolder.newFile();
    emptyFile.setExecutable(true);
    Reduce.mainHelper(new String[]{
        jsonFile.getAbsolutePath(),
        "CUSTOM",
        "--custom_judge",
        emptyFile.getAbsolutePath(),
        "--reduce_everywhere",
        "--output",
        temporaryFolder.getRoot().getAbsolutePath()}, null);
    final File[] reducedFinal = temporaryFolder.getRoot().listFiles((dir, name) -> name.contains(
        "reduced_final.frag"));
    assertEquals(1, reducedFinal.length);
    CompareAsts.assertEqualAsts("#version 100\nvoid main() { }",
        ParseHelper.parse(reducedFinal[0]));
  }

  private File getShaderJobReady() throws IOException, ParseTimeoutException {
    final String fragmentShader = "#version 100\n" +
        "int a;" +
        "int b;" +
        "int c;" +
        "void foo() { }" +
        "void main() {" +
        "  a = 2;" +
        "  b = 3;" +
        "  foo();" +
        "}";
    final File jsonFile = temporaryFolder.newFile("orig.json");
    final File fragmentFile = temporaryFolder.newFile("orig.frag");
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    fileOps.writeStringToFile(jsonFile, "{}");
    fileOps.writeStringToFile(fragmentFile, fragmentShader);
    fileOps.writeShaderJobFile(fileOps.readShaderJobFile(jsonFile), jsonFile);
    return jsonFile;
  }

  private String makeShaderJobAndReturnJsonFilename() throws IOException {
    temporaryFolder.newFile("shader.frag");
    final File jsonFile = temporaryFolder.newFile("shader.json");
    return jsonFile.getAbsolutePath();
  }

}