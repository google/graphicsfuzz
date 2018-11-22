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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.reducer.tool.GlslReduce;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ToolHelper;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.controlflow.AddDeadOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.AddJumpStmts;
import com.graphicsfuzz.generator.transformation.controlflow.AddLiveOutputVariableWrites;
import com.graphicsfuzz.generator.transformation.controlflow.SplitForLoops;
import com.graphicsfuzz.generator.transformation.donation.DonateDeadCode;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
import com.graphicsfuzz.generator.transformation.mutator.MutateExpressions;
import com.graphicsfuzz.generator.transformation.outliner.OutlineStatements;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.reducer.CheckAstFeatureVisitor;
import com.graphicsfuzz.reducer.CheckAstFeaturesFileJudge;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.tool.RandomFileJudge;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReducerUnitTest {

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testReductionSteps() throws Exception {
    List<ITransformationSupplier> transformations = new ArrayList<>();
    int seed = 0;
    for (File originalShaderJobFile : Util.getReferenceShaderJobFiles()) {
      testGenerateAndReduce(originalShaderJobFile, transformations, new RandomWrapper(seed));
      seed++;
    }
  }

  private void testGenerateAndReduce(File originalShaderJobFile,
                                     List<ITransformationSupplier> transformations,
                                     RandomWrapper generator) throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_100;
    final File referenceImage =
        Util.renderShader(
            shadingLanguageVersion,
            originalShaderJobFile,
            temporaryFolder,
            fileOps);
    final UniformsInfo uniformsInfo = new UniformsInfo(originalShaderJobFile);
    final TranslationUnit tu = generateSizeLimitedShader(
        fileOps.getUnderlyingShaderFile(originalShaderJobFile, ShaderKind.FRAGMENT),
        transformations, generator,
        shadingLanguageVersion);
    Generate.addInjectionSwitchIfNotPresent(tu);
    Generate.setInjectionSwitch(uniformsInfo);
    Generate.randomiseUnsetUniforms(tu, uniformsInfo, generator);
    tu.setShadingLanguageVersion(shadingLanguageVersion);

    final IdGenerator idGenerator = new IdGenerator();

    for (int step = 0; step < 10; step++) {
      List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(
          new GlslShaderJob(Optional.empty(), new UniformsInfo(), tu),
          new ReducerContext(false, shadingLanguageVersion, generator, idGenerator, true),
          fileOps);
      if (ops.isEmpty()) {
        break;
      }
      System.err.println("Step: " + step + "; ops: " + ops.size());
      ops.get(generator.nextInt(ops.size())).applyReduction();

      final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), uniformsInfo, tu);

      final File variantImage =
          Util.validateAndGetImage(
              shaderJob,
              originalShaderJobFile.getName() + "_reduced_" + step + ".frag",
              temporaryFolder,
              fileOps);
      Util.assertImagesSimilar(referenceImage, variantImage);
    }

  }

  private TranslationUnit generateSizeLimitedShader(File fragmentShader,
      List<ITransformationSupplier> transformations, IRandom generator,
      ShadingLanguageVersion shadingLanguageVersion) throws IOException, ParseTimeoutException {
    while (true) {
      List<ITransformationSupplier> transformationsCopy = new ArrayList<>();
      transformationsCopy.addAll(transformations);
      final TranslationUnit tu = ParseHelper.parse(fragmentShader);
      for (int i = 0; i < 4; i++) {
        getTransformation(transformationsCopy, generator).apply(
            tu, TransformationProbabilities.DEFAULT_PROBABILITIES, shadingLanguageVersion,
            generator, GenerationParams.normal(ShaderKind.FRAGMENT, true));
      }
      File tempFile = temporaryFolder.newFile();
      PrettyPrinterVisitor.emitShader(tu, Optional.empty(),
          new PrintStream(
              new FileOutputStream(tempFile)),
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          true
      );
      final int maxBytes = 100000;
      if (tempFile.length() <= maxBytes) {
        return tu;
      }
    }
  }

  private ITransformation getTransformation(List<ITransformationSupplier> transformations,
      IRandom generator) throws IOException {
    if (transformations.isEmpty()) {
      transformations.addAll(getTransformations());
    }
    return transformations.remove(generator.nextInt(transformations.size())).get();
  }

  private List<ITransformationSupplier> getTransformations() {
    List<ITransformationSupplier> result = new ArrayList<>();
    result.add(() -> new AddJumpStmts());
    result.add(() -> new MutateExpressions());
    result.add(() -> new OutlineStatements(new IdGenerator()));
    result.add(() -> new SplitForLoops());
    result.add(() -> new DonateDeadCode(
            TransformationProbabilities.DEFAULT_PROBABILITIES::donateDeadCodeAtStmt,
            Util.createDonorsFolder(temporaryFolder),
            GenerationParams.normal(ShaderKind.FRAGMENT, true)));
    result.add(() -> new DonateLiveCode(
            TransformationProbabilities.likelyDonateLiveCode()::donateLiveCodeAtStmt,
            Util.createDonorsFolder(temporaryFolder),
            GenerationParams.normal(ShaderKind.FRAGMENT, true),
            true));
    result.add(() -> new AddDeadOutputVariableWrites());
    result.add(() -> new AddLiveOutputVariableWrites());
    return result;
  }

  public void reduceRepeatedly(String shaderJobFileName, int numIterations,
        int threshold,
        boolean throwExceptionOnInvalid) throws Exception {

    final File shaderJobFile =
        Paths.get(TestShadersDirectory.getTestShadersDirectory(), "reducerregressions", shaderJobFileName).toFile();

    final ShaderJob initialState = fileOps.readShaderJobFile(shaderJobFile);
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion
        .getGlslVersionFromFirstTwoLines(fileOps.getFirstTwoLinesOfShader(shaderJobFile,
            ShaderKind.FRAGMENT));

    IRandom generator = new RandomWrapper(4);

    for (int i = 0; i < numIterations; i++) {

      final File workDir = temporaryFolder.newFolder();
      fileOps.copyShaderJobFileTo(shaderJobFile, new File(workDir, shaderJobFileName), false);
      final String shaderJobShortName = FilenameUtils.removeExtension(shaderJobFile.getName());

      new ReductionDriver(new ReducerContext(false,
          shadingLanguageVersion, generator,
            new IdGenerator(), true), false, fileOps, initialState)
            .doReduction(shaderJobShortName, 0,
                  new RandomFileJudge(generator, threshold, throwExceptionOnInvalid, fileOps),
                workDir,
                  100);
    }

  }

  @Test
  public void reducerRegression1() throws Exception  {
    reduceRepeatedly("shader1.json", 10, 10, true);
  }

  @Test
  public void reducerRegression2() throws Exception {
    reduceRepeatedly("shader2.json", 7, 10, false);
  }

  @Test
  public void reducerRegression3() throws Exception {
    reduceRepeatedly("shader3.json", 10, 3, true);
  }

  @Test
  public void reducerRegression4() throws Exception {
    reduceRepeatedly("shader3.json", 10, 10, true);
  }

  @Test
  public void testCrunchThroughLiveCode() throws Exception {
    final IFileJudge involvesSpecificBinaryOperator =
          new CheckAstFeaturesFileJudge(Arrays.asList(() -> new CheckAstFeatureVisitor() {
              @Override
              public void visitBinaryExpr(BinaryExpr binaryExpr) {
                super.visitBinaryExpr(binaryExpr);
                if (binaryExpr.getOp() == BinOp.MUL
                      && binaryExpr.getLhs() instanceof VariableIdentifierExpr
                      && ((VariableIdentifierExpr) binaryExpr.getLhs()).getName()
                      .equals("GLF_live3lifetimeFrac")
                      && binaryExpr.getRhs() instanceof VariableIdentifierExpr
                      && ((VariableIdentifierExpr) binaryExpr.getRhs()).getName()
                      .equals("GLF_live3initialDistance")) {
                  trigger();
                }
              }
            }), ShaderKind.FRAGMENT, fileOps);

    final File shaderJobFile = Paths.get(TestShadersDirectory.getTestShadersDirectory(),
        "reducerregressions", "misc1.json").toFile();
    final String outputFilesPrefix = runReductionOnShader(shaderJobFile,
        involvesSpecificBinaryOperator);
    PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(new File(temporaryFolder.getRoot(),
            outputFilesPrefix + ".frag")
    ));
    // TODO: assert something about the result.
  }

  @Test
  public void testIntricateReduction() throws Exception {

    final Supplier<CheckAstFeatureVisitor> involvesFloatLiteral1 = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
        super.visitFloatConstantExpr(floatConstantExpr);
        if (floatConstantExpr.getValue().equals("7066.7300")) {
          trigger();
        }
      }
    };

    final Supplier<CheckAstFeatureVisitor> involvesFloatLiteral2 = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
        super.visitFloatConstantExpr(floatConstantExpr);
        if (floatConstantExpr.getValue().equals("1486.9927")) {
          trigger();
        }
      }
    };

    final Supplier<CheckAstFeatureVisitor> involvesVariable1 = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals("GLF_live2pixel")) {
          trigger();
        }
      }
    };

    final Supplier<CheckAstFeatureVisitor> involvesVariable2 = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals("GLF_live2color")) {
          trigger();
        }
      }
    };

    final Supplier<CheckAstFeatureVisitor> involvesVariable3 = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals("GLF_live0INNER_ITERS")) {
          trigger();
        }
      }
    };

    final Supplier<CheckAstFeatureVisitor> involvesSpecialAssignment = () -> new CheckAstFeatureVisitor() {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (binaryExpr.getOp() != BinOp.ASSIGN) {
          return;
        }
        if (!(binaryExpr.getLhs() instanceof MemberLookupExpr)) {
          return;
        }
        if (!(((MemberLookupExpr) binaryExpr.getLhs()).getStructure() instanceof VariableIdentifierExpr)) {
          return;
        }
        if (!((VariableIdentifierExpr)((MemberLookupExpr) binaryExpr.getLhs()).getStructure()).getName()
              .equals("GLF_merged2_0_1_10_1_1_11GLF_live0tGLF_live0v2")) {
          return;
        }
        if (!((MemberLookupExpr) binaryExpr.getLhs()).getMember().equals("y")) {
          return;
        }
        if (!(binaryExpr.getRhs() instanceof VariableIdentifierExpr)) {
          return;
        }
        if (!(((VariableIdentifierExpr) binaryExpr.getRhs()).getName().equals("GLF_live0v2"))) {
          return;
        }
        trigger();
      }
    };

    IFileJudge judge = new CheckAstFeaturesFileJudge(Arrays.asList(
          involvesFloatLiteral1, involvesFloatLiteral2, involvesVariable1, involvesVariable2, involvesVariable3, involvesSpecialAssignment),
          ShaderKind.FRAGMENT, fileOps);
    final File shaderJobFile = Paths.get(TestShadersDirectory.getTestShadersDirectory(),
        "reducerregressions", "intricate.json").toFile();
    final String outputFilesPrefix = runReductionOnShader(shaderJobFile, judge);
    PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(new File(temporaryFolder.getRoot(),
            outputFilesPrefix + ".frag")
    ));
  }

  private String runReductionOnShader(File shaderJobFile, IFileJudge fileJudge)
        throws IOException, ParseTimeoutException {
    final String shaderJobShortName = FilenameUtils.removeExtension(shaderJobFile.getName());
    final ShadingLanguageVersion version =
        ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
            fileOps.getFirstTwoLinesOfShader(shaderJobFile, ShaderKind.FRAGMENT));
    // TODO: Use shaderJobFile.
    final IRandom generator = new RandomWrapper(0);
    final ShaderJob state = fileOps.readShaderJobFile(shaderJobFile);
    fileOps.copyShaderJobFileTo(shaderJobFile, new File(temporaryFolder.getRoot(),
        shaderJobFile.getName()), false);
    return new ReductionDriver(new ReducerContext(false, version, generator, new IdGenerator(), true), false, fileOps, state)
        .doReduction(shaderJobShortName, 0,
          fileJudge, temporaryFolder.getRoot(), -1);
  }

  @Test
  public void testBasicReduction() throws Exception {

    final String program =
          "#version 100\n"
          + "precision highp float;"
          + "void main() {"
          + "  float x = 0.0;"
          + "  float y = 0.0;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  x = x + 0.1 + y + y + y + y + y;"
          + "  gl_FragColor = vec4(x, 0.0, 0.0, 1.0);"
          + "}";
    final File reference = temporaryFolder.newFile("reference.frag");
    final File referenceJson = temporaryFolder.newFile("reference.json");
    final File referenceImage = temporaryFolder.newFile("reference.png");
    final File referenceJsonFakeResult = temporaryFolder.newFile("reference.info.json");
    FileUtils.writeStringToFile(reference, program, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJson, "{ }", StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJsonFakeResult, "{ }", StandardCharsets.UTF_8);

    final ExecResult referenceResult = ToolHelper.runSwiftshaderOnShader(RedirectType.TO_LOG,
          reference, referenceImage, false);
    assertEquals(0, referenceResult.res);

    final File output = temporaryFolder.newFolder();

    int numSteps = 20;

    GlslReduce.mainHelper(new String[] {
          referenceJson.getAbsolutePath(),
          "--swiftshader",
          "IDENTICAL",
          "--reduce_everywhere",
          "--reference",
          referenceJsonFakeResult.getAbsolutePath(),
          "--max_steps",
          String.valueOf(numSteps),
          "--seed",
          "0",
          "--output",
          output.getAbsolutePath()
    }, null);

    while (new File(output, Constants.REDUCTION_INCOMPLETE).exists()) {
      numSteps += 5;
      GlslReduce.mainHelper(new String[] {
            referenceJson.getAbsolutePath(),
            "--swiftshader",
            "IDENTICAL",
            "--reduce_everywhere",
            "--reference",
            referenceJsonFakeResult.getAbsolutePath(),
            "--max_steps",
            String.valueOf(numSteps),
            "--seed",
            "0",
            "--output",
            output.getAbsolutePath(),
            "--continue_previous_reduction"
      }, null);
    }

    final File[] finalResults = output.listFiles((dir, file)
          -> file.contains("final") && file.endsWith(".frag"));
    assertEquals(1, finalResults.length);
    assertEquals(
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse("#version 100\n"
              + "precision highp float;\n"
              + "void main() { gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0); }")),
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(finalResults[0])));
  }

  @Test(expected = FileNotFoundException.class)
  public void testConditionForContinuingReduction() throws Exception {
    final String program =
          "void main() {"
                + "}";
    final File shader = temporaryFolder.newFile("reference.frag");
    final File json = temporaryFolder.newFile("reference.json");
    FileUtils.writeStringToFile(shader, program, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(json, "{ }", StandardCharsets.UTF_8);
    final File output = temporaryFolder.newFolder();
    // This should throw a FileNotFoundException, because REDUCTION_INCOMPLETE
    // will not be present.
    GlslReduce.mainHelper(new String[] { "--swiftshader", "--continue_previous_reduction",
          json.getAbsolutePath(), "--output",
          output.getAbsolutePath(), "NO_IMAGE" }, null);
  }

  @Test
  public void checkReductionIsFinite() throws Exception {
    final String program =
          "#version 100\n"
                + "void main() {"
                + "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);"
                + "}\n";
    final File reference = temporaryFolder.newFile("reference.frag");
    final File referenceJson = temporaryFolder.newFile("reference.json");
    final File referenceJsonFakeResult = temporaryFolder.newFile("reference.info.json");
    final File referenceImage = temporaryFolder.newFile("reference.png");
    FileUtils.writeStringToFile(reference, program, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJson, "{ }", StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(referenceJsonFakeResult, "{ }", StandardCharsets.UTF_8);

    final ExecResult referenceResult = ToolHelper.runSwiftshaderOnShader(RedirectType.TO_LOG,
          reference, referenceImage, false);
    assertEquals(0, referenceResult.res);
    final File output = temporaryFolder.newFolder();
    GlslReduce.mainHelper(new String[] {
          referenceJson.getAbsolutePath(),
          "--swiftshader",
          "IDENTICAL",
          "--reduce_everywhere",
          "--reference",
          referenceJsonFakeResult.getAbsolutePath(),
          "--max_steps",
          "-1",
          "--seed",
          "0",
          "--output",
          output.getAbsolutePath() }, null);
  }

}
