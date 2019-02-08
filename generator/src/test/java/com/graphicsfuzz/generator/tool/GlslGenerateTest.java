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

import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.IdentityTransformation;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class GlslGenerateTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testSmall100ShaderFamilies() throws Exception {
    final String references = Paths.get(ToolPaths.getShadersDirectory(),
        "samples", "100").toString();
    final String donors = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "donors").toString();
    final int numVariants = 3;
    final String prefix = "someprefix";
    final String outputDir = temporaryFolder.getRoot().getAbsolutePath();
    final int seed = 0;
    checkShaderFamilyGeneration(references, donors, numVariants, prefix,
        outputDir, seed, Collections.singletonList("--stop-on-fail"));
  }

  @Test
  public void testVulkanShaderFamilies() throws Exception {
    final String references = Paths.get(ToolPaths.getShadersDirectory(),
        "samples", "310es").toString();
    final String donors = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "donors").toString();
    final int numVariants = 3;
    final String prefix = "someprefix";
    final String outputDir = temporaryFolder.getRoot().getAbsolutePath();
    final int seed = 1;
    checkShaderFamilyGeneration(references, donors, numVariants, prefix,
        outputDir, seed, Arrays.asList("--generate-uniform-bindings", "--max-uniforms",
            String.valueOf(10), "--stop-on-fail", "--max-factor", String.valueOf(100f),
            "--max-bytes", String.valueOf(500000), "--disable",
            DonateDeadCode.NAME + "," + IdentityTransformation.NAME));
  }

  @Test
  public void testDoNotGenerateFamilyForBadReference() throws Exception {
    final String badFragmentSource = "this is not a fragment shader";
    final String goodFragmentSource = "#version 100\nprecision mediump float;\nvoid main() { }";
    final File references = temporaryFolder.newFolder();
    final File badReferenceFrag = new File(references, "bad.frag");
    final File badReferenceJson = new File(references, "bad.json");
    FileUtils.writeStringToFile(badReferenceFrag, badFragmentSource, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(badReferenceJson, "{}", StandardCharsets.UTF_8);
    final File goodReferenceFrag = new File(references, "good.frag");
    final File goodReferenceJson = new File(references, "good.json");
    FileUtils.writeStringToFile(goodReferenceFrag, goodFragmentSource, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(goodReferenceJson, "{}", StandardCharsets.UTF_8);

    final String donors = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "donors").toString();

    final File outputDir = temporaryFolder.newFolder();

    final int numVariants = 2;
    final String prefix = "family";
    generateShaderFamily(references.getAbsolutePath(), donors, numVariants, prefix,
        outputDir.getAbsolutePath(),
        0, new ArrayList<>());

    final File unexpectedOutputDirectory = new File(outputDir, prefix + "_bad");
    // We should not get a directory for the bad reference.
    assertFalse(unexpectedOutputDirectory.exists());

    final File expectedOutputDirectory = new File(outputDir, prefix + "_good");
    assertTrue(expectedOutputDirectory.isDirectory());

    assertTrue(new File(expectedOutputDirectory, "infolog.json").isFile());
    assertTrue(new File(expectedOutputDirectory, "reference.frag").isFile());
    assertTrue(new File(expectedOutputDirectory, "reference.json").isFile());
    for (int i = 0; i < numVariants; i++) {
      assertTrue(new File(expectedOutputDirectory,
          "variant_" + String.format("%03d", i) + ".frag").isFile());
      assertTrue(new File(expectedOutputDirectory,
          "variant_" + String.format("%03d", i) + ".json").isFile());
    }

  }

  private void checkShaderFamilyGeneration(String references,
                                          String donors,
                                          int numVariants,
                                          String prefix,
                                          String outputDir,
                                          int seed,
                                          List<String> extraArgs)
      throws ArgumentParserException,
      InterruptedException,
      IOException,
      ParseTimeoutException,
      GlslParserException {

    generateShaderFamily(references, donors, numVariants, prefix, outputDir, seed, extraArgs);

    for (String reference : Arrays.asList("bubblesort_flag", "colorgrid_modulo",
        "mandelbrot_blurry", "prefix_sum", "squares")) {
      final File expectedOutputDirectory = new File(temporaryFolder.getRoot(), prefix
          + "_" + reference);
      assertTrue(expectedOutputDirectory.isDirectory());
      assertTrue(new File(expectedOutputDirectory, "infolog.json").isFile());
      assertTrue(new File(expectedOutputDirectory, "reference.frag").isFile());
      assertTrue(new File(expectedOutputDirectory, "reference.json").isFile());
      for (int i = 0; i < numVariants; i++) {
        assertTrue(new File(expectedOutputDirectory,
            "variant_" + String.format("%03d", i) + ".frag").isFile());
        assertTrue(new File(expectedOutputDirectory,
            "variant_" + String.format("%03d", i) + ".json").isFile());
      }
    }
  }

  private void generateShaderFamily(String references,
                                   String donors,
                                   int numVariants,
                                   String prefix,
                                   String outputDir,
                                   int seed,
                                   List<String> extraArgs)
      throws ArgumentParserException,
      InterruptedException,
      IOException,
      ParseTimeoutException,
      GlslParserException {
    final List<String> options = new ArrayList<>();

    options.addAll(Arrays.asList(
        references,
        donors,
        String.valueOf(numVariants),
        prefix,
        outputDir,
        "--seed",
        String.valueOf(seed),
        "--verbose"
    ));

    options.addAll(extraArgs);

    GlslGenerate.mainHelper(
        options.toArray(new String[0])
    );
  }

}
