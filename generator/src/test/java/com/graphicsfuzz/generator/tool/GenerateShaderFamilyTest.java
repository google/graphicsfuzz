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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class GenerateShaderFamilyTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testGenerateSmall100ShaderFamily() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "bubblesort_flag";
    final int numVariants = 3;
    int seed = 0;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmallWebGL1ShaderFamily() throws Exception {
    final String samplesSubdir = "webgl1";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    int seed = 1;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmall300esShaderFamily() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "mandelbrot_blurry";
    final int numVariants = 3;
    int seed = 2;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamily() throws Exception {
    final String samplesSubdir = "webgl2";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 3;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmallVulkanShaderFamily() throws Exception {
    final String samplesSubdir = "310es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 4;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--generate-uniform-bindings", "--single-pass"));
  }

  @Test
  public void testGenerateSmall100ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "squares";
    final int numVariants = 3;
    int seed = 5;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"));
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
        seed, Arrays.asList("--stop-on-fail"));
  }

  @Test
  public void testGenerateSmall300esShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    int seed = 7;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"));
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "webgl2";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    int seed = 8;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail"));
  }

  @Test
  public void testGenerateSmallVulkanShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "310es";
    final String referenceShaderName = "bubblesort_flag";
    final int numVariants = 3;
    int seed = 9;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--generate-uniform-bindings"));
  }

  @Test
  public void testGenerateSmallVulkanShaderFamilyWithReferencesAsDonors() throws Exception {
    // This is designed to guard against bugs whereby donating 310 es features into a 310 es shader
    // causes problems
    final String samplesSubdir = "310es";
    final String referenceShaderName = "squares";
    final int numVariants = 3;
    final int seed = 0;
    final String reference = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "310es", "squares.json").toString();
    // Note that we are using the 310es samples as donors here.
    final String donors = Paths.get(ToolPaths.getShadersDirectory(),"samples",
        "310es").toString();

    final List<String> extraOptions = Arrays.asList(
        "--stop-on-fail",
        "--max-uniforms",
        String.valueOf(10),
        "--generate-uniform-bindings");
    checkShaderFamilyGeneration(numVariants, seed, extraOptions, reference, donors);
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

    final String donors = Paths.get(ToolPaths.getShadersDirectory(),"samples",
        "donors").toString();
    // Disable shader_translator, so we should still get family generated
    checkShaderFamilyGeneration(2, 0, Collections.singletonList("--disable-shader-translator"),
        referenceJsonFile.getAbsolutePath(),
        donors);
  }

  @Test
  public void testIgnoreGlslangValidator() throws Exception {
    // shader_translator will not be invoked on this shader, and glslangValidator would reject it
    // due to it using a made up extension.
    final String reference = "#version 440\n"
        + "#extension does_not_exist : nothing\n"
        + "\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "}\n";
    final File referenceFragFile = temporaryFolder.newFile("reference.frag");
    final File referenceJsonFile = temporaryFolder.newFile("reference.json");
    FileUtils.writeStringToFile(referenceFragFile, reference, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJsonFile, "{}", StandardCharsets.UTF_8);

    final String donors = Paths.get(ToolPaths.getShadersDirectory(),"samples",
        "donors").toString();
    // Disabling glslangValidator should lead to a family being generated, as the rest of the tool
    // chain will just ignore the imaginary extension.
    checkShaderFamilyGeneration(2, 0,
        Collections.singletonList("--disable-glslangValidator"),
        referenceJsonFile.getAbsolutePath(),
        donors);
  }

  private void checkShaderFamilyGeneration(String samplesSubdir, String referenceShaderName,
                                          int numVariants, int seed,
                                          List<String> extraOptions) throws ArgumentParserException,
      InterruptedException, IOException, ReferencePreparationException {
    final String reference = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        samplesSubdir, referenceShaderName
        + ".json").toString();
    final String donors = Paths.get(ToolPaths.getShadersDirectory(),"samples",
        "donors").toString();

    checkShaderFamilyGeneration(numVariants, seed, extraOptions, reference, donors);
  }

  private void checkShaderFamilyGeneration(int numVariants, int seed, List<String> extraOptions, String reference,
                                           String donors) throws ArgumentParserException, InterruptedException, IOException, ReferencePreparationException {
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

}
