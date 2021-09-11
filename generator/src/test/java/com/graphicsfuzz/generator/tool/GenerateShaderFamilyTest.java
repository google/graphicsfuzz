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

import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GenerateShaderFamilyTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @After
  public void deleteTemporarySpirvFiles() {
    // In Vulkan mode, glslangValidator will create SPIR-V files. This cleans them up after each
    // test.
    for (String kind : Arrays.asList("vert", "frag", "comp")) {
      final File tempFile = new File(kind + ".spv");
      if (tempFile.exists()) {
        tempFile.delete();
      }
    }
  }

  @Test
  public void testGenerateSmall100ShaderFamily() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "stable_bubblesort_flag";
    final int numVariants = 3;
    int seed = 0;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"),
        ShadingLanguageVersion.ESSL_100);
  }

  @Test
  public void testGenerateSmallWebGL1ShaderFamily() throws Exception {
    final String samplesSubdir = "webgl1";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    int seed = 1;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"),
        ShadingLanguageVersion.WEBGL_SL);
  }

  @Test
  public void testGenerateSmall300esShaderFamily() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "mandelbrot_zoom";
    final int numVariants = 3;
    int seed = 2;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"),
        ShadingLanguageVersion.ESSL_300);
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamily() throws Exception {
    final String samplesSubdir = "webgl2";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 3;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"),
        ShadingLanguageVersion.WEBGL2_SL);
  }

  @Test
  public void testGenerateSmallVulkanShaderFamily() throws Exception {
    final String samplesSubdir = "320es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 4;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--vulkan", "--single-pass"), ShadingLanguageVersion.ESSL_320);
  }

  @Test
  public void testGenerateSmall100ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "squares";
    final int numVariants = 3;
    int seed = 5;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"), ShadingLanguageVersion.ESSL_100);
  }

  // TODO(172)
  @Ignore
  @Test
  public void testGenerateSmallWebGL1ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "webgl1";
    final String referenceShaderName = "mandelbrot_blurry";
    final int numVariants = 3;
    int seed = 6;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"), ShadingLanguageVersion.WEBGL_SL);
  }

  @Test
  public void testGenerateSmall300esShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    int seed = 7;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"), ShadingLanguageVersion.ESSL_300);
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "webgl2";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 8;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"), ShadingLanguageVersion.WEBGL2_SL);
  }

  @Test
  public void testGenerateSmallVulkanShaderFamilyFrom320EsMultiPass() throws Exception {
    final String samplesSubdir = "320es";
    final String referenceShaderName = "stable_bubblesort_flag";
    final int numVariants = 3;
    int seed = 9;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--vulkan"), ShadingLanguageVersion.ESSL_320);
  }

  @Test
  public void testGenerateSmallVulkanShaderFamilyFrom450MultiPass() throws Exception {
    final String samplesSubdir = "450";
    final String referenceShaderName = "stable_bubblesort_flag";
    final int numVariants = 3;
    int seed = 9;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--vulkan"), ShadingLanguageVersion.ESSL_320);
  }

  @Test
  public void testIgnoreShaderTranslator() throws Exception {
    // shader_translator would reject this due to the non-constant array access; glslangValidator
    // accepts it.
    final String reference = "#version 100\n"
        + "//WebGL\n"
        + "\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  int x;\n"
        + "  int A[5];\n"
        + "  A[x] = 2;\n"
        + "}\n";
    final File referenceFragFile = temporaryFolder.newFile("reference.frag");
    final File referenceJsonFile = temporaryFolder.newFile("reference.json");
    FileUtils.writeStringToFile(referenceFragFile, reference, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJsonFile, "{}", StandardCharsets.UTF_8);

    final String donors = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "webgl1").toString();
    // Disable shader_translator, so we should still get family generated
    checkShaderFamilyGeneration(2, 0,
        Collections.singletonList("--disable-shader-translator"),
        referenceJsonFile.getAbsolutePath(),
        donors);
  }

  @Test
  public void testIgnoreGlslangValidator() throws Exception {
    // shader_translator will not be invoked on this shader, and glslangValidator would reject it
    // due to it using a made up extension.
    final String reference = "#version 320 es\n"
        + "#extension does_not_exist : nothing\n"
        + "\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "}\n";
    final File referenceFragFile = temporaryFolder.newFile("reference.frag");
    final File referenceJsonFile = temporaryFolder.newFile("reference.json");
    FileUtils.writeStringToFile(referenceFragFile, reference, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJsonFile, "{}", StandardCharsets.UTF_8);

    final String donors = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "320es").toString();
    // Disabling glslangValidator should lead to a family being generated, as the rest of the tool
    // chain will just ignore the imaginary extension.
    checkShaderFamilyGeneration(2, 0,
        Collections.singletonList("--disable-glslangValidator"),
        referenceJsonFile.getAbsolutePath(),
        donors);
  }

  private void checkShaderFamilyGeneration(String samplesSubdir, String referenceShaderName,
                                           int numVariants, int seed,
                                           List<String> extraOptions,
                                           ShadingLanguageVersion shadingLanguageVersion)
      throws ArgumentParserException, InterruptedException, IOException,
      ReferencePreparationException {

    final File samplesPrepared = temporaryFolder.newFolder();
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    for (File shaderJobFile : fileOperations
        .listShaderJobFiles(Paths.get(ToolPaths.getShadersDirectory(), "samples", samplesSubdir)
            .toFile())) {
      final File newShaderJobFile = new File(samplesPrepared,
          shaderJobFile.getName());
      fileOperations.copyShaderJobFileTo(shaderJobFile, newShaderJobFile, false);
      maybeAddVertexShaderIfMissing(shadingLanguageVersion, fileOperations, newShaderJobFile);
    }

    final String reference = Paths.get(samplesPrepared.getAbsolutePath(), referenceShaderName
        + ".json").toString();
    checkShaderFamilyGeneration(numVariants, seed, extraOptions, reference,
        samplesPrepared.getAbsolutePath());
  }

  private void checkShaderFamilyGeneration(int numVariants,
                                           int seed,
                                           List<String> extraOptions,
                                           String reference,
                                           String donors)
      throws ArgumentParserException, InterruptedException, IOException,
      ReferencePreparationException {
    final List<String> options = new ArrayList<>();

    options.addAll(Arrays.asList(
        reference,
        donors,
        temporaryFolder.getRoot().getAbsolutePath(),
        "--seed",
        String.valueOf(seed),
        "--num-variants",
        String.valueOf(numVariants),
        "--verbose"
    ));

    options.addAll(extraOptions);

    GenerateShaderFamily.mainHelper(
        options.toArray(new String[0])
    );

    assertTrue(new File(temporaryFolder.getRoot(), "infolog.json").isFile());
    assertTrue(new File(temporaryFolder.getRoot(), "reference.frag").isFile());
    assertTrue(new File(temporaryFolder.getRoot(), "reference.json").isFile());
    for (int i = 0; i < numVariants; i++) {
      assertTrue(new File(temporaryFolder.getRoot(),
          "variant_" + String.format("%03d", i) + ".frag").isFile());
      assertTrue(new File(temporaryFolder.getRoot(),
          "variant_" + String.format("%03d", i) + ".json").isFile());
    }
  }

  /**
   * If the shading language version is 300 es or higher, and there is a fragment shader but no
   * vertex shader, a trivial vertex shader is added.  This is to give vertex shader transformation
   * a workout for these shading language versions, and to ensure that attempts are not made to
   * donate code between shader kinds.
   */
  private void maybeAddVertexShaderIfMissing(ShadingLanguageVersion shadingLanguageVersion,
                                             ShaderJobFileOperations fileOperations,
                                             File newShaderJobFile) throws IOException {
    if (!Arrays.asList(ShadingLanguageVersion.ESSL_300, ShadingLanguageVersion.ESSL_320,
        ShadingLanguageVersion.ESSL_320).contains(shadingLanguageVersion)) {
      return;
    }
    if (fileOperations.doesShaderExist(newShaderJobFile, ShaderKind.FRAGMENT)
        && !fileOperations.doesShaderExist(newShaderJobFile, ShaderKind.VERTEX)) {
      final String trivialVertexShader = "#version " + shadingLanguageVersion.getVersionString()
          + "\n"
          + "\n"
          + "layout (location = 0) in vec4 pos;\n"
          + "\n"
          + "void main() {\n"
          + "   gl_Position = pos;\n"
          + "}\n";
      fileOperations.writeStringToFile(fileOperations.getUnderlyingShaderFile(newShaderJobFile,
          ShaderKind.VERTEX), trivialVertexShader);
    }
  }

}
