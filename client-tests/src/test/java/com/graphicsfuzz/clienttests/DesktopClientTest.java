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

package com.graphicsfuzz.clienttests;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.graphicsfuzz.util.ToolPaths;
import com.graphicsfuzz.server.FuzzerServer;
import com.graphicsfuzz.shadersets.ShaderDispatchException;

public class DesktopClientTest extends CommonClientTest {

  @BeforeClass
  public static void startServerAndWorker() throws Exception {
    File serverWorkDir = temporaryFolder.newFolder();
    server = new Thread(() -> {
      try {
        final FuzzerServer fuzzerServer = new FuzzerServer(
            serverWorkDir.getAbsolutePath(), 8080, fileOps);
        fuzzerServer.start();
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    });
    server.start();

    final String clientJar = getClientJar();
    final List<String> command = Arrays.asList(
        "java", "-jar", clientJar);
    final ProcessBuilder pb =
        new ProcessBuilder()
            .command(command)
            .directory(temporaryFolder.getRoot());

    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

    FileUtils.writeStringToFile(new File(temporaryFolder.getRoot(), "token.txt"),
        TOKEN, StandardCharsets.UTF_8);

    worker = pb.start();

    int exceptionCount = 0;
    final int limit = 1000;
    File workerDirectory = Paths.get(serverWorkDir.getAbsolutePath(), "processing", TOKEN)
        .toFile();
    while (true) {
      Thread.sleep(10);
      if (workerDirectory.exists()) {
        break;
      }
      if (exceptionCount >= limit) {
        throw new RuntimeException("Problem starting worker and server");
      }
      exceptionCount++;
    }
    System.out.println("Got token after " + exceptionCount + " tries");
  }

  @AfterClass
  public static void destroyWorker() {
    worker.destroy();
  }

  private static String getClientJar() throws URISyntaxException {
    final File file = new File(ToolPaths.class.getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .toURI()).getAbsoluteFile().getParentFile();
    return Paths.get(ToolPaths.getSourceRoot(file), "gles-worker",
        "desktop", "build", "libs", "gles-worker-desktop-1.0.jar").toString();
  }

  @Test
  public void cubeWithTexture() throws Exception {
    File outputDir = runShader("cube_with_texture.frag");
    checkStatus(new File(outputDir, "cube_with_texture.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "cube_with_texture.png");
    final File actual = new File(outputDir, "cube_with_texture.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void icos() throws Exception {
    File outputDir = runShader("icos.frag");
    checkStatus(new File(outputDir, "icos.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "icos.png");
    final File actual = new File(outputDir, "icos.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void cubeScene() throws Exception {
    File outputDir = runShader("cube_scene.frag");
    checkStatus(new File(outputDir, "cube_scene.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "cube_scene.png");
    final File actual = new File(outputDir, "cube_scene.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void cubeSceneInterestingVertexShader() throws Exception {
    File outputDir = runShader("cube_scene_interesting_vertex_shader.frag");
    checkStatus(new File(outputDir, "cube_scene_interesting_vertex_shader.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "cube_scene.png");
    final File actual = new File(outputDir, "cube_scene_interesting_vertex_shader.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void compileFailComputeES() throws Exception {
    File outputDir = runComputeShader("compile_fail.comp", temporaryFolder);
    checkStatus(new File(outputDir, "compile_fail.info.json"), "COMPILE_ERROR");
    // This is driver-dependent:
    // checkComputeShaderLogContains(new File(outputDir, "compile_fail.info.json"), "undeclared");
  }

  @Test
  public void simpleComputeShaderES() throws Exception {
    File outputDir = runComputeShader("42es.comp", temporaryFolder);
    checkStatus(new File(outputDir, "42es.info.json"), "SUCCESS");
    checkOutput(new File(outputDir, "42es.info.json"), new int[] { 42 });
  }

  @Test
  public void computeShaderReduction() throws Exception {
    File outputDir = runComputeShader("reduction.comp", temporaryFolder);
    checkStatus(new File(outputDir, "reduction.info.json"), "SUCCESS");
    checkOutputPrefix(new File(outputDir, "reduction.info.json"), new int[] { 8128 });
  }

  @Test
  public void computeShaderAtomicSum() throws Exception {
    File outputDir = runComputeShader("sum.comp", temporaryFolder);
    checkStatus(new File(outputDir, "sum.info.json"), "SUCCESS");
    checkOutputPrefix(new File(outputDir, "sum.info.json"), new int[] { 73536 + 666 });
  }

  static File runComputeShader(String computeShader, TemporaryFolder temporaryFolder)
      throws IOException, InterruptedException, ArgumentParserException, ShaderDispatchException {
    final File outputDir = temporaryFolder.newFolder();
    String[] args = {
        FilenameUtils.removeExtension(
            Paths.get(getTestShadersDirectory(), computeShader).toString()) + ".json",
        "--token", TOKEN, "--server", "http://localhost:8080", "--output",
        outputDir.getAbsolutePath()
    };

    RunShaderFamily.mainHelper(args, null);

    return outputDir;
  }


  private void checkComputeShaderLogContains(File jsonFile, String expected) throws FileNotFoundException {
    JsonObject json = new Gson().fromJson(new FileReader(jsonFile),
        JsonObject.class);
    assertTrue(json.get("Log").getAsString().contains(expected));
  }

  private void checkOutput(File jsonFile, int[] expected) throws FileNotFoundException {
    JsonObject json = new Gson().fromJson(new FileReader(jsonFile),
        JsonObject.class);
    assertArrayEquals(expected, getIntArray(json.get("Outputs").getAsJsonObject().get("output").getAsJsonArray()));
  }

  private void checkOutputPrefix(File jsonFile, int[] expectedPrefix) throws FileNotFoundException {
    JsonObject json = new Gson().fromJson(new FileReader(jsonFile),
          JsonObject.class);
    final int[] actualData = getIntArray(
          json.get("Outputs").getAsJsonObject().get("output").getAsJsonArray());
    for (int i = 0; i < expectedPrefix.length; i++) {
      assertEquals(expectedPrefix[i], actualData[i]);
    }
  }

  public static int[] getIntArray(JsonArray argsJson) {
    int[] intArgs = new int[argsJson.size()];
    Iterator<JsonElement> it = argsJson.iterator();
    for(int i = 0; i < intArgs.length; ++i) {
      intArgs[i] = it.next().getAsInt();
    }
    return intArgs;
  }

}
