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
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.transformation.AddDeadOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddJumpTransformation;
import com.graphicsfuzz.generator.transformation.AddLiveOutputWriteTransformation;
import com.graphicsfuzz.generator.transformation.AddWrappingConditionalTransformation;
import com.graphicsfuzz.generator.transformation.DonateDeadCodeTransformation;
import com.graphicsfuzz.generator.transformation.DonateLiveCodeTransformation;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.IdentityTransformation;
import com.graphicsfuzz.generator.transformation.OutlineStatementTransformation;
import com.graphicsfuzz.generator.transformation.SplitForLoopTransformation;
import com.graphicsfuzz.generator.transformation.StructificationTransformation;
import com.graphicsfuzz.generator.transformation.VectorizeTransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GeneratorUnitTest {

  // Toggle this to 'true' to focus on cases that used to fail but may
  // now work due to swiftshader fixes.
  private static final boolean reverseExclusionList = false;

  // Toggle this to 'true' to run tests on all shaders.
  private static final boolean ignoreExclusionList = false;

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testRenderBlackImage() throws Exception {
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n"
        + "}\n";
    fileOps.writeShaderJobFile(
        new GlslShaderJob(Optional.empty(), new PipelineInfo(), ParseHelper.parse(shader)),
        shaderJobFile);

    final File image = Util.getImage(shaderJobFile, temporaryFolder, fileOps);
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
    testTransformationMultiVersions(StructificationTransformation::new,
        TransformationProbabilities.DEFAULT_PROBABILITIES,
        "structs");
  }

  @Test
  public void testDeadJumps() throws Exception {
    testTransformationMultiVersions(AddJumpTransformation::new,
        TransformationProbabilities.onlyAddJumps(),
        "jumps");
  }

  @Test
  public void testIdentity() throws Exception {
    testTransformationMultiVersions(IdentityTransformation::new,
        TransformationProbabilities
        .onlyMutateExpressions(), "mutate");
  }

  @Test
  public void testOutlineStatements() throws Exception {
    testTransformationMultiVersions(OutlineStatementTransformation::new,
        TransformationProbabilities.onlyOutlineStatements(),
        "outline");
  }

  @Test
  public void testSplitForLoops() throws Exception {
    testTransformationMultiVersions(SplitForLoopTransformation::new, TransformationProbabilities
        .onlySplitLoops(), "split");
  }

  @Test
  public void testDonateDeadCode() throws Exception {
    testTransformationMultiVersions(() -> new DonateDeadCodeTransformation(
        TransformationProbabilities.likelyDonateDeadCode()::donateDeadCodeAtStmt,
            Util.getDonorsFolder(),
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true)),
        TransformationProbabilities.likelyDonateDeadCode(),
        "donatedead",
        Arrays.asList("bubblesort_flag.json", "squares.json", "mandelbrot_zoom.json"),
        Arrays.asList("bubblesort_flag.json", "squares.json", "mandelbrot_zoom.json"));
    // Reason for excluding^: slow.
  }

  @Test
  public void testDonateLiveCode() throws Exception {
    testTransformationMultiVersions(() -> new DonateLiveCodeTransformation(
        TransformationProbabilities.likelyDonateLiveCode()::donateLiveCodeAtStmt,
            Util.getDonorsFolder(),
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true),
        false), TransformationProbabilities.likelyDonateLiveCode(),
        "donatelive",
        Collections.singletonList("squares.json"),
        Collections.singletonList("squares.json"));
    // Reason for excluding^: slow.
  }

  @Test
  public void testAddDeadFragColorWrites() throws Exception {
    testTransformationMultiVersions(AddDeadOutputWriteTransformation::new,
        TransformationProbabilities
        .onlyAddDeadFragColorWrites(), "deadfragcolor", Collections.emptyList(),
        Collections.emptyList());
  }

  @Test
  public void testAddLiveOutputVariableWrites() throws Exception {
    testTransformationMultiVersions(AddLiveOutputWriteTransformation::new,
        TransformationProbabilities
        .onlyAddLiveFragColorWrites(), "liveoutvar", Collections.emptyList(),
        Collections.emptyList());
  }

  @Test
  public void testVectorize() throws Exception {
    testTransformationMultiVersions(VectorizeTransformation::new,
        TransformationProbabilities.onlyVectorize(),
        "vectorize", Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void mutateAndVectorize() throws Exception {
    testTransformationMultiVersions(Arrays.asList(IdentityTransformation::new,
        VectorizeTransformation::new),
        TransformationProbabilities.onlyVectorizeAndMutate(),
        "mutate_and_vectorize",
        Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void testStructification() throws Exception {
    testTransformationMultiVersions(StructificationTransformation::new,
        TransformationProbabilities.onlyStructify(),
        "structify", Collections.emptyList(), Collections.emptyList());
  }

  @Test
  public void testWrap() throws Exception {
    testTransformationMultiVersions(AddWrappingConditionalTransformation::new,
        TransformationProbabilities.onlyWrap(),
        "wrap",
        Arrays.asList("bubblesort_flag.json", "colorgrid_modulo.json"),
        Arrays.asList("bubblesort_flag.json", "colorgrid_modulo.json"));
    // Reason for excluding^: slow.
  }

  @Test
  public void testIdentityNotNot() throws Exception {
    // Designed to give the expr -> !!expr many chances to apply.

    String shader = "#version 300 es\n"
        + "precision highp float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "void main() {"
        + "_GLF_color = vec4(0.0, 0.0, 0.0, 1.0);";
    for (int i = 0; i < 10; i++) {
      shader += "if ((true)) ";
    }
    shader += "_GLF_color = vec4(1.0, 0.0, 1.0, 1.0);";
    shader += "}\n";
    testTransformationForSpecificShader(
        Collections.singletonList(IdentityTransformation::new),
        TransformationProbabilities.onlyMutateExpressions(),
        "mutate.frag",
        shader);
  }

  private void testTransformation(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> exclusionList,
        File[] referenceFiles)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    for (File originalShaderJobFile : referenceFiles) {
      final List<ITransformation> transformationsList = new ArrayList<>();
      for (ITransformationSupplier supplier : transformations) {
        transformationsList.add(supplier.get());
      }
      boolean skipRender;
      if (ignoreExclusionList) {
        skipRender = false;
      } else {
        skipRender =
            (reverseExclusionList != exclusionList.contains(originalShaderJobFile.getName()));
      }
      File referenceImage = null;
      if (!skipRender) {
        referenceImage = Util.validateAndGetImage(originalShaderJobFile,
            temporaryFolder,
            fileOps);
      }
      generateAndCheckVariant(
          transformationsList,
          probabilities,
          suffix,
          originalShaderJobFile,
          referenceImage,
          skipRender);
    }
  }

  private void testTransformation100(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> exclusionList)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    testTransformation(transformations, probabilities, suffix, exclusionList,
          Util.getReferenceShaderJobFiles100es(fileOps));
  }

  private void testTransformation300es(List<ITransformationSupplier> transformations,
        TransformationProbabilities probabilities, String suffix, List<String> exclusionList)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    testTransformation(transformations, probabilities, suffix, exclusionList,
        Util.getReferenceShaderJobFiles300es(fileOps));
  }

  private void testTransformationMultiVersions(List<ITransformationSupplier> transformations,
                                               TransformationProbabilities probabilities,
                                               String suffix,
                                               List<String> exclusionList100,
                                               List<String> exclusionList300es)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    testTransformation100(transformations, probabilities, suffix, exclusionList100);
    testTransformation300es(transformations, probabilities, suffix, exclusionList300es);
  }

  private void testTransformationMultiVersions(ITransformationSupplier transformation,
      TransformationProbabilities probabilities, String suffix,
                                               List<String> exclusionList100,
                                               List<String> exclusionList300es)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    testTransformationMultiVersions(Collections.singletonList(transformation), probabilities,
        suffix, exclusionList100, exclusionList300es);
  }

  private void testTransformationMultiVersions(ITransformationSupplier transformation,
      TransformationProbabilities probabilities, String suffix)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    testTransformationMultiVersions(Collections.singletonList(transformation), probabilities,
        suffix,
        new ArrayList<>(), new ArrayList<>());
  }

  private void generateAndCheckVariant(
      List<ITransformation> transformations,
      TransformationProbabilities probabilities,
      String suffix,
      File originalShaderJobFile,
      File referenceImage,
      boolean skipRender
  ) throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    // skipRender if and only if reference image is null.
    assert skipRender == (referenceImage == null);

    final ShaderJob shaderJob = fileOps.readShaderJobFile(originalShaderJobFile);
    assertEquals(1, shaderJob.getShaders().size());
    assertEquals(ShaderKind.FRAGMENT, shaderJob.getShaders().get(0).getShaderKind());
    final TranslationUnit tu = shaderJob.getShaders().get(0);

    final RandomWrapper generator = new RandomWrapper(originalShaderJobFile.getName().hashCode());
    for (ITransformation transformation : transformations) {
      transformation.apply(tu, probabilities,
          generator,
          GenerationParams.normal(ShaderKind.FRAGMENT, false, true));
    }
    Generate.addInjectionSwitchIfNotPresent(tu);
    Generate.setInjectionSwitch(shaderJob.getPipelineInfo());
    Generate.randomiseUnsetUniforms(tu, shaderJob.getPipelineInfo(), generator);

    // Write shaders to shader job file and validate.
    final File shaderJobFile =
        Paths.get(
            temporaryFolder.getRoot().getAbsolutePath(),
            originalShaderJobFile.getName() + suffix + ".json"
        ).toFile();

    fileOps.writeShaderJobFile(
        shaderJob,
        shaderJobFile
    );
    assertTrue(fileOps.areShadersValid(shaderJobFile, false));

    if (!skipRender) {
      final File variantImage = Util.getImage(shaderJobFile, temporaryFolder, fileOps);
      Util.assertImagesSimilar(referenceImage, variantImage);
    }

  }

  private void testTransformationForSpecificShader(List<ITransformationSupplier> transformations,
                                                   TransformationProbabilities probabilities,
                                                   String suffix,
                                                   String shader)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final File referenceFrag = new File(temporaryFolder.getRoot(), "shader.frag");
    final File referenceJson = new File(temporaryFolder.getRoot(), "shader.json");
    FileUtils.writeStringToFile(referenceFrag, shader, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJson, "{}", StandardCharsets.UTF_8);
    testTransformation(transformations, probabilities, suffix, Collections.emptyList(),
        new File[] { referenceJson });
  }

  @Test
  public void testDonateDeadCodeNoCompatibleDonors() throws Exception {
    // By design, we set up a situation where the reference and donors are all identical
    // and all declare a struct, which makes them incompatible (because at the moment we
    // conservatively say that a donor and reference are incompatible if they declare structs
    // with the same name).
    //
    // Dead code donation will not be able to succeed, but should have no effect rather than
    // aborting.
    final File donors = temporaryFolder.newFolder();
    final ShaderJob declaresSingleStruct = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse("#version 300 es\n"
          + "precision highp float;\n"
          + "struct S {\n"
          + "  int x;\n"
          + "};\n"
          + "void main() {\n"
          + "}\n"));
    for (int i = 0; i < 3; i++) {
      fileOps.writeShaderJobFile(declaresSingleStruct, new File(donors, "donor_" + i + ".json"));
    }
    final File reference = temporaryFolder.newFile("reference.json");
    fileOps.writeShaderJobFile(declaresSingleStruct, reference);
    testTransformation(Collections.singletonList(() -> new DonateDeadCodeTransformation(
            TransformationProbabilities.likelyDonateDeadCode()::donateDeadCodeAtStmt,
            donors,
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true))),
        TransformationProbabilities.likelyDonateDeadCode(),
        "donatedead", Collections.emptyList(),
        new File[] { reference });
  }

}
