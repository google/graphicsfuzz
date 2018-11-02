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

package com.graphicsfuzz.tester;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.AvoidDeprecatedGlFragColor;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.controlflow.AddDeadOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddJumpStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddLiveOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.transformation.controlflow.SplitForLoops;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.transformation.outliner.OutlineStatements;
import com.graphicsfuzz.generator.transformation.structifier.Structification;
import com.graphicsfuzz.generator.transformation.vectorizer.VectorizeStatements;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GeneratorUnitTest {

  // Toggle this to 'true' to focus on cases that used to fail but may
  // now work due to swiftshader fixes.
  private static final boolean reverseBlacklist = false;

  // Toggle this to 'true' to run tests on all shaders.
  private static final boolean ignoreBlacklist = false;

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testRenderBlackImage() throws Exception {
    File shaderFile = temporaryFolder.newFile("shader.frag");

    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n"
        + "}\n";
    BufferedWriter bw = new BufferedWriter(new FileWriter(shaderFile));
    bw.write(shader);
    bw.close();

    final File image = Util.getImageUsingSwiftshader(shaderFile, temporaryFolder);
    final Mat mat = opencv_imgcodecs.imread(image.getAbsolutePath());

    final UByteIndexer sI = mat.createIndexer();
    for (int y = 0; y < sI.rows(); y++) {
      for (int x = 0; x < sI.cols(); x++) {
        if ((x % 3) == 2) {
          assertEquals(255, sI.get(y, x));
        } else {
          assertEquals(0, sI.get(y, x));
        }
      }
    }
  }

  @Test
  public void testStructify() throws Exception {
    testTransformationMultiVersions(() -> new Structification(), TransformationProbabilities.DEFAULT_PROBABILITIES,
        "structs.frag");
  }

  @Test
  public void testDeadJumps() throws Exception {
    testTransformationMultiVersions(() -> new AddJumpStmts(), TransformationProbabilities.onlyAddJumps(),
        "jumps.frag");
  }

  @Test
  public void testMutateExpressions() throws Exception {
    testTransformationMultiVersions(() -> new MutateExpressions(), TransformationProbabilities
        .onlyMutateExpressions(), "mutate.frag");
  }

  @Test
  public void testOutlineStatements() throws Exception {
    testTransformationMultiVersions(() -> new OutlineStatements(new IdGenerator()),
        TransformationProbabilities.onlyOutlineStatements(),
        "outline.frag");
  }

  @Test
  public void testSplitForLoops() throws Exception {
    testTransformationMultiVersions(() -> new SplitForLoops(), TransformationProbabilities
        .onlySplitLoops(), "split.frag");
  }

  @Test
  public void testDonateDeadCode() throws Exception {
    testTransformationMultiVersions(() -> new DonateDeadCode(
        TransformationProbabilities.likelyDonateDeadCode()::donateDeadCodeAtStmt,
        Util.createDonorsFolder(temporaryFolder),
        GenerationParams.normal(ShaderKind.FRAGMENT, true)), TransformationProbabilities.likelyDonateDeadCode(),
        "donatedead.frag", Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testDonateLiveCode() throws Exception {
    testTransformationMultiVersions(() -> new DonateLiveCode(
        TransformationProbabilities.likelyDonateLiveCode()::donateLiveCodeAtStmt,
        Util.createDonorsFolder(temporaryFolder),
        GenerationParams.normal(ShaderKind.FRAGMENT, true),
        true), TransformationProbabilities.likelyDonateLiveCode(),
        "donatelive.frag",
        Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testAddDeadFragColorWrites() throws Exception {
    testTransformationMultiVersions(() -> new AddDeadOutputVariableWrites(), TransformationProbabilities
        .onlyAddDeadFragColorWrites(), "deadfragcolor.frag", Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testAddLiveOutputVariableWrites() throws Exception {
    testTransformationMultiVersions(() -> new AddLiveOutputVariableWrites(), TransformationProbabilities
        .onlyAddLiveFragColorWrites(), "liveoutvar.frag", Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testVectorize() throws Exception {
    testTransformationMultiVersions(() -> new VectorizeStatements(),
        TransformationProbabilities.onlyVectorize(),
        "vectorize.frag", Arrays.asList(), Arrays.asList());
  }

  @Test
  public void mutateAndVectorize() throws Exception {
    testTransformationMultiVersions(Arrays.asList(() -> new MutateExpressions(), () -> new VectorizeStatements()),
        TransformationProbabilities.onlyVectorizeAndMutate(),
        "mutate_and_vectorize.frag",
        Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testStructification() throws Exception {
    testTransformationMultiVersions(() -> new Structification(),
        TransformationProbabilities.onlyStructify(),
        "structify.frag", Arrays.asList(), Arrays.asList());
  }

  @Test
  public void testWrap() throws Exception {
    testTransformationMultiVersions(() -> new AddWrappingConditionalStmts(),
        TransformationProbabilities.onlyWrap(),
        "wrap.frag",
        Arrays.asList("colorgrid_modulo.json", "prefix_sum.json"),
        Arrays.asList("colorgrid_modulo.json", "prefix_sum.json"));
  }

  private void testTransformation(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> blacklist,
        ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, ParseTimeoutException, InterruptedException {
    for (File originalShaderJobFile : Util.getReferenceShaderJobFiles()) {
      final List<ITransformation> transformationsList = new ArrayList<>();
      for (ITransformationSupplier supplier : transformations) {
        transformationsList.add(supplier.get());
      }
      boolean skipRender;
      if (ignoreBlacklist) {
        skipRender = false;
      } else {
        skipRender = (reverseBlacklist != blacklist.contains(originalShaderJobFile.getName()));
      }
      final File referenceImage = Util.renderShader(
          ShadingLanguageVersion.ESSL_100, // The references are all in ESSL 1.00 format
          originalShaderJobFile,
          temporaryFolder,
          fileOps);
      generateAndCheckVariant(transformationsList,
          probabilities,
          suffix,
          originalShaderJobFile, shadingLanguageVersion,
          referenceImage,
          skipRender);
    }
  }

  private void testTransformation100(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> blacklist)
        throws IOException, ParseTimeoutException, InterruptedException {
    testTransformation(transformations, probabilities, suffix, blacklist,
          ShadingLanguageVersion.ESSL_100);
  }

  private void testTransformation300es(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> blacklist)
        throws IOException, ParseTimeoutException, InterruptedException {
    testTransformation(transformations, probabilities, suffix, blacklist,
          ShadingLanguageVersion.ESSL_300);
  }

  private void testTransformationMultiVersions(List<ITransformationSupplier> transformations,
                                               TransformationProbabilities probabilities,
                                               String suffix,
                                               List<String> blacklist100,
                                               List<String> blacklist300es)
        throws IOException, ParseTimeoutException, InterruptedException {
    testTransformation100(transformations, probabilities, suffix, blacklist100);
    testTransformation300es(transformations, probabilities, suffix, blacklist300es);
  }

  private void testTransformationMultiVersions(ITransformationSupplier transformation,
      TransformationProbabilities probabilities, String suffix,
                                               List<String> blacklist100,
                                               List<String> blacklist300es)
      throws IOException, ParseTimeoutException, InterruptedException {
    testTransformationMultiVersions(Arrays.asList(transformation), probabilities,
        suffix, blacklist100, blacklist300es);
  }

  private void testTransformationMultiVersions(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix)
        throws IOException, ParseTimeoutException, InterruptedException {
    testTransformationMultiVersions(transformations, probabilities, suffix, new ArrayList<>(),
        new ArrayList<>());
  }

  private void testTransformationMultiVersions(ITransformationSupplier transformation,
      TransformationProbabilities probabilities, String suffix)
      throws IOException, ParseTimeoutException, InterruptedException {
    testTransformationMultiVersions(Arrays.asList(transformation), probabilities, suffix,
        new ArrayList<>(), new ArrayList<>());
  }

  private void generateAndCheckVariant(List<ITransformation> transformations,
      TransformationProbabilities probabilities, String suffix, File originalShaderJobFile,
      ShadingLanguageVersion shadingLanguageVersion,
      File referenceImage,
      boolean skipRender) throws IOException, ParseTimeoutException, InterruptedException {
    final ShaderJob shaderJob = fileOps.readShaderJobFile(originalShaderJobFile);
    assertEquals(1, shaderJob.getShaders().size());
    assertEquals(ShaderKind.FRAGMENT, shaderJob.getShaders().get(0).getShaderKind());
    final TranslationUnit tu = shaderJob.getShaders().get(0);

    if (!shadingLanguageVersion.supportedGlFragColor()) {
      AvoidDeprecatedGlFragColor.avoidDeprecatedGlFragColor(shaderJob.getShaders().get(0),
          Constants.GLF_COLOR);
    }
    final RandomWrapper generator = new RandomWrapper(originalShaderJobFile.getName().hashCode());
    for (ITransformation transformation : transformations) {
      transformation.apply(tu, probabilities, shadingLanguageVersion,
          generator,
          GenerationParams.normal(ShaderKind.FRAGMENT, true));
    }
    Generate.addInjectionSwitchIfNotPresent(tu);
    Generate.setInjectionSwitch(shaderJob.getUniformsInfo());
    Generate.randomiseUnsetUniforms(tu, shaderJob.getUniformsInfo(), generator);
    tu.setShadingLanguageVersion(shadingLanguageVersion);

    // Using fileOps, even though the rest of the code here does not use it yet.
    // Write shaders to shader job file and validate.

    Assert.assertTrue(suffix.endsWith(".frag"));
    // e.g. "_matrix_mult"
    final String suffixNoExtension = FilenameUtils.removeExtension(suffix);
    // e.g. "temp/orig_matrix_mult.json"
    final File shaderJobFile =
        Paths.get(
            temporaryFolder.getRoot().getAbsolutePath(),
            originalShaderJobFile.getName() + suffixNoExtension + ".json"
        ).toFile();

    fileOps.writeShaderJobFile(
        shaderJob,
        shaderJobFile
    );
    fileOps.areShadersValid(shaderJobFile, true);

    if (!skipRender) {
      File underlyingFragFile = fileOps.getUnderlyingShaderFile(shaderJobFile, ShaderKind.FRAGMENT);
      // TODO: Use fileOps.
      final File variantImage = Util.getImage(underlyingFragFile, temporaryFolder, fileOps);
      Util.assertImagesSimilar(referenceImage, variantImage);
    }

  }

}
