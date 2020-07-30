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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.ExecHelper;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GlslReduceTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void noServerAllowedInCustomReduction() throws Exception {
    try {

      GlslReduce.mainHelper(new String[]{"--server", "some_server", "--worker", "some_worker",
              makeShaderJobAndReturnJsonFilename(), "--reduction-kind", "CUSTOM", "--output",
              temporaryFolder.getRoot().getAbsolutePath()}, null);
      fail();
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "server");
    }
  }

  @Test
  public void noWorkerNameAllowedInCustomReduction() throws Exception {
    try {
      GlslReduce.mainHelper(new String[]{"--worker", "some_worker",
          makeShaderJobAndReturnJsonFilename(), "--reduction-kind", "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "worker");
    }
  }

  @Test
  public void noErrorStringAllowedInCustomReduction() throws Exception {
    try {
      GlslReduce.mainHelper(new String[]{"--error-string", "some_string",
          makeShaderJobAndReturnJsonFilename(), "--reduction-kind", "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      assertTrue(false);
    } catch (RuntimeException exception) {
      checkOptionNotAllowed(exception, "error-string");
    }
  }

  @Test
  public void noReferenceAllowedInCustomReduction() throws Exception {
    try {
      GlslReduce.mainHelper(new String[]{"--reference", "reference.info.json",
          makeShaderJobAndReturnJsonFilename(),
          "--reduction-kind", "CUSTOM", "--output", temporaryFolder.getRoot().getAbsolutePath()},
          null);
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
      GlslReduce.mainHelper(new String[]{makeShaderJobAndReturnJsonFilename(),
          "--reduction-kind", "NO_IMAGE",
          "somejudgescript", "--output",
          temporaryFolder.getRoot().getAbsolutePath()},
          null);
      fail();
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("An interestingness test is only supported when "
          + "a custom reduction is used."));
    }
  }

  @Test
  public void customJudgeRequiredInCustomReduction() throws Exception {
    try {
      GlslReduce.mainHelper(new String[]{makeShaderJobAndReturnJsonFilename(), "--reduction-kind",
          "CUSTOM", "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      fail();
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("A custom reduction requires an interestingness "
          + "test to be specified."));
    }

  }

  @Test
  public void checkCustomJudgeIsExecutable() throws Exception {
    if (ExecHelper.IS_WINDOWS) {
      // All files are executable on Windows.
      return;
    }
    final File jsonFile = getShaderJobReady();
    final File emptyFile = temporaryFolder.newFile(); // Not executable
    try {
      GlslReduce.mainHelper(new String[]{
          jsonFile.getAbsolutePath(),
          "--reduction-kind",
          "CUSTOM",
          emptyFile.getAbsolutePath(),
          "--output",
          temporaryFolder.getRoot().getAbsolutePath()}, null);
      fail("An exception should have been thrown as the judge script is not executable.");
    } catch (RuntimeException exception) {
      assertTrue(exception.getMessage().contains("judge script must be executable"));
    }
  }

  @Test
  public void testAlwaysReduceJudgeMaximallyReduces() throws Exception {
    final File jsonFile = getShaderJobReady();
    // We make this a .bat file to avoid a "what application would you like to use to open this
    // file?" pop-up on Windows.  (On other platforms the fact that it has the .bat extension does
    // not matter.)
    final File emptyFile = temporaryFolder.newFile("judge.bat");
    emptyFile.setExecutable(true);
    GlslReduce.mainHelper(new String[]{
        jsonFile.getAbsolutePath(),
        "--reduction-kind",
        "CUSTOM",
        emptyFile.getAbsolutePath(),
        "--output",
        temporaryFolder.getRoot().getAbsolutePath()}, null);
    final File[] reducedFinal = temporaryFolder.getRoot().listFiles((dir, name) -> name.contains(
        "reduced_final.frag"));
    assertEquals(1, reducedFinal.length);
    CompareAsts.assertEqualAsts("#version 100\nvoid main() { }",
        ParseHelper.parse(reducedFinal[0]));
  }

  private File getShaderJobReady() throws IOException, ParseTimeoutException, InterruptedException,
      GlslParserException {
    final String fragmentShader = "#version 100\n"
        + "int a;"
        + "int b;"
        + "int c;"
        + "void foo() { }"
        + "void main() {"
        + "  a = 2;"
        + "  b = 3;"
        + "  foo();"
        + "}";
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
