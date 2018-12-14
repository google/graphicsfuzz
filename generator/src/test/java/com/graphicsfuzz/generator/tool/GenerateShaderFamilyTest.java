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

import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
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
    final String glslVersionString = "100";
    int seed = 0;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmallWebGL1ShaderFamily() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    final String glslVersionString = "100";
    int seed = 1;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--webgl", "--single-pass"));
  }

  @Test
  public void testGenerateSmall300esShaderFamily() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "mandelbrot_blurry";
    final int numVariants = 3;
    final String glslVersionString = "300 es";
    int seed = 2;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--single-pass"));
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamily() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    final String glslVersionString = "300 es";
    int seed = 3;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--webgl", "--single-pass"));
  }

  @Test
  public void testGenerateSmallVulkanShaderFamily() throws Exception {
    final String samplesSubdir = "310es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    final String glslVersionString = "310 es";
    int seed = 4;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--generate-uniform-bindings", "--single-pass"));
  }

  @Test
  public void testExceptionWhenTryingToGenerateWebGL310EsShaderFamily() throws Exception {
    final String samplesSubdir = "310es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    final String glslVersionString = "310 es";
    int seed = 0;
    try {
      checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
          glslVersionString, seed, Collections.singletonList("--webgl"));
      fail("Runtime exception expected");
    } catch (RuntimeException expected) {
      // Check that we get a WebGL-related runtime exception.
      assertTrue(expected.getMessage().contains("WebGL"));
    }
  }

  @Test
  public void testGenerateSmall100ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "squares";
    final int numVariants = 3;
    final String glslVersionString = "100";
    int seed = 5;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail"));
  }

  // TODO(172)
  @Ignore
  @Test
  public void testGenerateSmallWebGL1ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "100";
    final String referenceShaderName = "mandelbrot_blurry";
    final int numVariants = 3;
    final String glslVersionString = "100";
    int seed = 6;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--webgl"));
  }

  @Test
  public void testGenerateSmall300esShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "colorgrid_modulo";
    final int numVariants = 3;
    final String glslVersionString = "300 es";
    int seed = 7;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail"));
  }

  @Test
  public void testGenerateSmallWebGL2ShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "300es";
    final String referenceShaderName = "prefix_sum";
    final int numVariants = 3;
    final String glslVersionString = "300 es";
    int seed = 8;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--webgl"));
  }

  @Test
  public void testGenerateSmallVulkanShaderFamilyMultiPass() throws Exception {
    final String samplesSubdir = "310es";
    final String referenceShaderName = "bubblesort_flag";
    final int numVariants = 3;
    final String glslVersionString = "310 es";
    int seed = 9;
    checkShaderFamilyGeneration(samplesSubdir, referenceShaderName, numVariants,
        glslVersionString, seed, Arrays.asList("--stop-on-fail", "--max-uniforms",
            String.valueOf(10),
            "--generate-uniform-bindings"));
  }

  private void checkShaderFamilyGeneration(String samplesSubdir, String referenceShaderName,
                                          int numVariants, String glslVersionString, int seed,
                                          List<String> extraOptions) throws ArgumentParserException,
      InterruptedException, IOException, ParseTimeoutException {
    final String reference = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        samplesSubdir, referenceShaderName
        + ".json").toString();
    final String donors = Paths.get(ToolPaths.getShadersDirectory(),"samples",
        "donors").toString();

    final List<String> options = new ArrayList<>();

    options.addAll(Arrays.asList(
        reference,
        donors,
        glslVersionString,
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