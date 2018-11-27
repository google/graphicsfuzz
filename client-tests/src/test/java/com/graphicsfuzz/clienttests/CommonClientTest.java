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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.shadersets.RunShaderFamily;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.apache.commons.io.FilenameUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.graphicsfuzz.util.ToolPaths;
import com.graphicsfuzz.common.util.ImageUtil;
import com.graphicsfuzz.shadersets.ShaderDispatchException;

public abstract class CommonClientTest {

  static final String WORKERNAME = "test_worker";
  static Process worker;
  static Thread server;
  static final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @ClassRule
  public static TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void fragmentCompileError() throws Exception {
    File outputDir = runShader("bad_fragment.frag");
    checkStatus(new File(outputDir, "bad_fragment.info.json"), "COMPILE_ERROR");
  }

  @Test
  public void fragmentShader() throws Exception {
    File outputDir = runShader("simple.frag");
    checkStatus(new File(outputDir, "simple.info.json"), "SUCCESS");
    File expected = new File(getResourcesDirectory(), "simple.png");
    File actual = new File(outputDir, "simple.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void vertexAndFragmentShaderTrivial() throws Exception {
    File outputDir = runShader("ultra_simple_with_vertex.frag");
    checkStatus(new File(outputDir, "ultra_simple_with_vertex.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "ultra_simple_with_vertex.png");
    final File actual = new File(outputDir, "ultra_simple_with_vertex.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void vertexAndFragmentShader() throws Exception {
    File outputDir = runShader("simple_with_vertex.frag");
    checkStatus(new File(outputDir, "simple_with_vertex.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "simple_with_vertex.png");
    final File actual = new File(outputDir, "simple_with_vertex.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void vertexCompileError() throws Exception {
    File outputDir = runShader("bad_vertex.frag");
    checkStatus(new File(outputDir, "bad_vertex.info.json"), "COMPILE_ERROR");
  }

  @Test
  public void linkError() throws Exception {
    File outputDir = runShader("link_error.frag");
    checkStatus(new File(outputDir, "link_error.info.json"), "LINK_ERROR");
  }

  @Test
  public void cube() throws Exception {
    File outputDir = runShader("cube.frag");
    checkStatus(new File(outputDir, "cube.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "cube.png");
    final File actual = new File(outputDir, "cube.png");
    assertSimilarImages(expected, actual);
  }

  @Test
  public void smallerTrianglesSimple() throws Exception {
    File outputDir = runShader("smaller_triangles_simple.frag");
    checkStatus(new File(outputDir, "smaller_triangles_simple.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "smaller_triangles_simple.png");
    final File actual = new File(outputDir, "smaller_triangles_simple.png");
    assertSimilarImages(expected, actual, 350.0);
  }

  @Test
  public void smallerTrianglesShrunk() throws Exception {
    File outputDir = runShader("smaller_triangles_shrunk.frag");
    checkStatus(new File(outputDir, "smaller_triangles_shrunk.info.json"), "SUCCESS");
    final File expected = new File(getResourcesDirectory(), "smaller_triangles_shrunk.png");
    final File actual = new File(outputDir, "smaller_triangles_shrunk.png");
    assertSimilarImages(expected, actual, 100.0);
  }

  static File runShader(String fragmentShader)
      throws ShaderDispatchException, InterruptedException, IOException, ArgumentParserException {
    final File outputDir = temporaryFolder.newFolder();
    String[] args = {
        FilenameUtils.removeExtension(Paths.get(getTestShadersDirectory(), fragmentShader).toString()) + ".json",
        "--worker", WORKERNAME, "--server", "http://localhost:8080", "--output",
        outputDir.getAbsolutePath()};
    RunShaderFamily.mainHelper(
        args, null
    );
    return outputDir;
  }

  static String getTestShadersDirectory() {
    File jarDir = ToolPaths.getJarDirectory();
    if (ToolPaths.isRunningFromIde(jarDir)) {
      return Paths.get(
          ToolPaths.getSourceRoot(jarDir),
          "client-tests",
          "src",
          "test",
          "glsl").toString();
    }
    throw new RuntimeException("Only supporting testing from IDE now.");
  }

  void checkStatus(File jsonFile, String expectedStatus) throws FileNotFoundException {
    JsonObject json = new Gson().fromJson(new FileReader(jsonFile),
        JsonObject.class);
    assertEquals(expectedStatus, json.get("status").getAsString());
  }

  static String getResourcesDirectory() {
    File jarDir = ToolPaths.getJarDirectory();
    if (ToolPaths.isRunningFromIde(jarDir)) {
      return Paths.get(
          ToolPaths.getSourceRoot(jarDir),
          "client-tests",
          "resources").toString();
    }
    throw new RuntimeException("Only supporting testing from IDE now.");
  }

  void assertSimilarImages(File expected, File actual) throws FileNotFoundException {
    assertSimilarImages(expected, actual, 50.0);
  }

  void assertSimilarImages(File expected, File actual, double threshold) throws FileNotFoundException {
    double histoDistance = ImageUtil.compareHistograms(
        ImageUtil.getHistogram(expected.getAbsolutePath()),
        ImageUtil.getHistogram(actual.getAbsolutePath()));
    assertTrue(histoDistance < threshold);
  }

}
