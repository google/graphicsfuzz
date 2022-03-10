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

package com.graphicsfuzz.reducer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.FileHelper;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.MakeShaderJobFromFragmentShader;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReductionDriverTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void doReduction() throws Exception {

    File tempFile = testFolder.newFile("temp.frag");
    File tempJsonFile = testFolder.newFile("temp.json");

    String program = "void main() { if(_GLF_DEAD(_GLF_FALSE(false, false))) { } }";

    FileUtils.writeStringToFile(tempFile, program, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(tempJsonFile, "{ }", StandardCharsets.UTF_8);

    ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    IRandom generator = new RandomWrapper(0);

    TranslationUnit tu = ParseHelper.parse(tempFile);

    ShaderJob state = new GlslShaderJob(
        Optional.empty(),
        new PipelineInfo(tempJsonFile),
        tu);

    IFileJudge pessimist = new IFileJudge() {

      // Says interesting first time, and uninteresting thereafter.

      boolean first = true;

      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput
      ) {
        if (first) {
          first = false;
          return true;
        }
        return false;
      }
    };

    List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(
                MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, true, version, generator, new IdGenerator()),
                fileOps);
    assertEquals(3, ops.size());

    new ReductionDriver(
        new ReducerContext(
            false,
            true,
            version,
            generator,
            new IdGenerator()),
        false,
        fileOps, pessimist, testFolder.getRoot())
        .doReduction(state, getPrefix(tempFile), 0, -1);

  }

  @Test
  public void checkUnsuccessfulReductionLeavesTrace() throws Exception {
    final String finalFilePrefix = reduce((unused, item) -> false,
          "void main() { }", "{ }",
        false);
    assertNull(finalFilePrefix);
    final File traceFile = new File(testFolder.getRoot(), "NOT_INTERESTING");
    assertTrue(traceFile.exists());
  }

  @Test
  public void testInitialStateCheckedMultipleTimes() throws Exception {
    IFileJudge judge = new IFileJudge() {
      private int count = 0;
      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput) {
        count++;
        return count == 3;
      }
    };

    final String finalFilePrefix =
        reduce(judge, "void main() { }", "{ }", false);
    assertNotNull(finalFilePrefix);
  }

  @Test
  public void testInitialStateNotCheckedExcessively() throws Exception {
    IFileJudge judge = new IFileJudge() {
      private int count = 0;
      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput) {
        count++;
        return count == 10;
      }
    };

    final String finalFilePrefix = reduce((unused, item) -> false, "void main() { }", "{ }",
        false);
    assertNull(finalFilePrefix);
  }

  private String reduce(IFileJudge judge, String program, String jsonString,
                        boolean reduceEverywhere)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return reduce(judge, program, jsonString, reduceEverywhere,
          -1, 0);
  }

  private String reduce(IFileJudge judge, String fragmentShader, String jsonString,
                        boolean reduceEverywhere,
                        int stepLimit,
                        int seed)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return reduce(judge, fragmentShader, Optional.empty(), jsonString,
        reduceEverywhere,
        stepLimit,
        seed);
  }

  private String reduce(IFileJudge judge,
                        String fragmentShader,
                        Optional<String> vertexShader,
                        String jsonString,
                        boolean reduceEverywhere,
                        int stepLimit,
                        int seed)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    assertFalse(new File(testFolder.getRoot(), "temp.frag").exists());
    File tempFragmentShaderFile = testFolder.newFile("temp.frag");
    Optional<File> tempVertexShaderFile = vertexShader.isPresent()
        ? Optional.of(testFolder.newFile("temp.vert")) : Optional.empty();
    File tempJsonFile = testFolder.newFile("temp.json");

    FileUtils.writeStringToFile(tempFragmentShaderFile, fragmentShader, StandardCharsets.UTF_8);

    if (vertexShader.isPresent()) {
      FileUtils.writeStringToFile(tempVertexShaderFile.get(), vertexShader.get(),
          StandardCharsets.UTF_8);
    }

    FileUtils.writeStringToFile(tempJsonFile, jsonString, StandardCharsets.UTF_8);

    ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    IRandom generator = new RandomWrapper(seed);

    TranslationUnit tuFrag = ParseHelper.parse(tempFragmentShaderFile);
    Optional<TranslationUnit> tuVert = vertexShader.isPresent()
        ? Optional.of(ParseHelper.parse(tempVertexShaderFile.get()))
        : Optional.empty();

    final List<TranslationUnit> translationUnits = new ArrayList<>();
    tuVert.ifPresent(translationUnits::add);
    translationUnits.add(tuFrag);
    ShaderJob state = new GlslShaderJob(
        Optional.empty(), new PipelineInfo(tempJsonFile),
        translationUnits);

    return new ReductionDriver(new ReducerContext(reduceEverywhere, true, version,
        generator, new IdGenerator()), false, fileOps,
        judge, testFolder.getRoot())
        .doReduction(state, getPrefix(tempFragmentShaderFile), 0, stepLimit);
  }


  @Test
  public void testInitializersAreInlined() throws Exception {
    final String original = "void main() {"
          + "    float GLF_live3_x = 3.0 + sin(7.0);\n"
          + "    float GLF_live3_y = GLF_live3_x + cos(8.0);\n"
          + "}\n";

    final String expected = "void main() {"
          + "    float GLF_live3_x = 3.0;\n"
          + "    float GLF_live3_y = sin(7.0) + cos(8.0);\n"
          + "}\n";

    final File tempFile = testFolder.newFile("temp.frag");
    final File tempJsonFile = testFolder.newFile("temp.json");

    FileUtils.writeStringToFile(tempFile, original, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(tempJsonFile, "{ }", StandardCharsets.UTF_8);

    final ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    final IRandom generator = new RandomWrapper(0);

    final TranslationUnit tu = ParseHelper.parse(tempFile);

    ShaderJob state = new GlslShaderJob(
        Optional.empty(), new PipelineInfo(tempJsonFile), tu);

    IFileJudge referencesSinCosAnd3 = (shaderJobFile, shaderResultFileOutput) -> {
      try {
        final String contents = FileUtils.readFileToString(
            FileHelper.replaceExtension(shaderJobFile, ".frag"), StandardCharsets.UTF_8);
        return contents.contains("float GLF_live3_x = 3.0 + sin(7.0);")
                    && contents.contains("float GLF_live3_y = GLF_live3_x + cos(8.0);")
            || contents.contains("float GLF_live3_x = 3.0 + sin(7.0);")
                    && contents.contains("float GLF_live3_y = (3.0 + sin(7.0)) + cos(8.0);")
            || contents.contains("float GLF_live3_x = 3.0;")
                    && contents.contains("float GLF_live3_y = (3.0 + sin(7.0)) + cos(8.0);")
            || contents.contains("float GLF_live3_x = 3.0;")
                    && contents.contains("float GLF_live3_y = (sin(7.0)) + cos(8.0);")
            || contents.contains("float GLF_live3_x = 3.0;")
                    && contents.contains("float GLF_live3_y = sin(7.0) + cos(8.0);");
      } catch (IOException e) {
        return false;
      }
    };

    final String reducedFilesPrefix = new ReductionDriver(
        new ReducerContext(false, true, version, generator, new IdGenerator()),
        false, fileOps,
        referencesSinCosAnd3, testFolder.getRoot())
        .doReduction(state, getPrefix(tempFile), 0, -1);

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(
              new File(testFolder.getRoot(), reducedFilesPrefix + ".frag"))));

  }

  @Test
  public void testLiveGlFragColorWriteOpportunity() throws Exception {
    IFileJudge judge = (shaderJobFile, shaderResultFileOutput) -> {
      try {
        return
            FileUtils.readFileToString(
                FileHelper.replaceExtension(shaderJobFile, ".frag"), StandardCharsets.UTF_8
            ).contains("if(true)");
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      }
    };
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + "color";
    final String resultFilesPrefix = reduce(judge, "layout(location = 0) out vec4 color;\n"
            + "void main() {"
            + "  {"
            + "    {\n"
            + "       vec4 " + backupName + ";\n"
            + "       " + backupName + " = color;\n"
            + "       color = vec4(- 6439.8706, 306.836, 60.88, 9418.3243);\n"
            + "       color = " + backupName + ";\n"
            + "       if(true) {"
            + "       }\n"
            + "    }"
            + "  }"
            + "}", "{ }", false);
    final String expected = "void main() {"
          + "   if(true) {"
          + "   }"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(
                ParseHelper.parse(new File(testFolder.getRoot(), resultFilesPrefix + ".frag"))));
  }

  @Test
  public void testInlineReduceEverywhere() throws Exception {
    final IFileJudge judge = new CheckAstFeaturesFileJudge(
        Collections.singletonList(
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
                super.visitFunctionCallExpr(functionCallExpr);
                if (functionCallExpr.getCallee().equals("sin")) {
                  trigger();
                }
              }
            }), ShaderKind.FRAGMENT, fileOps);

    final String resultFilesPrefix = reduce(judge, "float foo(float a) { return sin(a); }"
                + "void main() {"
                + "  float f = foo(42.0);"
                + "}",
          "{ }", true);
    final String expected = "void main() {"
          + "   sin(1.0);"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(
                ParseHelper.parse(new File(testFolder.getRoot(), resultFilesPrefix + ".frag"))));
  }

  @Test
  public void testInlineLiveCode() throws Exception {
    final IFileJudge judge = new CheckAstFeaturesFileJudge(
        Collections.singletonList(
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
                super.visitFunctionCallExpr(functionCallExpr);
                if (functionCallExpr.getCallee().equals("sin")) {
                  trigger();
                }
              }
            }), ShaderKind.FRAGMENT, fileOps);

    final String resultFilesPrefix = reduce(judge,
        "vec3 GLF_live3intersects(vec3 GLF_live3src, vec3 GLF_live3direction) {"
                + "  vec3 GLF_live3temp = GLF_live3src + GLF_live3direction;"
                + "  if (GLF_live3temp.x > 3.0) {"
                + "    return sin(GLF_live3direction);"
                + "  }"
                + "  return cos(1.0);"
                + "}"
                + ""
                + "vec3 GLF_live3intermediate(vec3 GLF_live3a, vec3 GLF_live3b, vec3 GLF_live3c) {"
                + "  return GLF_live3intersects(GLF_live3a, GLF_live3b);"
                + "}"
                + ""
                + "void main() {"
                + "  vec3 GLF_live3x;"
                + "  GLF_live3x = GLF_live3intermediate(vec3(3.0), vec3(1.2, 2.3, 3.4), vec3(7.0));"
                + "}",
          "{ }", false);
    final String expected = "void main() {"
          + "   sin(vec3(1.0));"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(
                ParseHelper.parse(new File(testFolder.getRoot(), resultFilesPrefix + ".frag"))));
  }

  @Test
  public void testIncompleteReductionEndsCorrectly() throws Exception {
    final IFileJudge interestingFirstTime = new IFileJudge() {
      private boolean first = true;

      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput) throws FileJudgeException {
        boolean result = first;
        first = false;
        return result;
      }
    };

    final String program = ""
          + "void main() {"
          + "  int x;"
          + "  x = 3;"
          + "}";

    final String json = "{ }";

    final String resultFilesPrefix = reduce(interestingFirstTime, program, json, true, 2, 0);

    assertEquals("temp_reduced_final", FilenameUtils.getBaseName(resultFilesPrefix));

    assertTrue(new File(testFolder.getRoot(), Constants.REDUCTION_INCOMPLETE).exists());

    CompareAsts.assertEqualAsts(program,
        ParseHelper.parse(new File(testFolder.getRoot(), resultFilesPrefix + ".frag")));

  }

  @Test
  public void testNoReductionLoop() throws Exception {
    String program = "void main()\n"
          + "{\n"
          + "    {\n"
          + "     vec4 _GLF_gl_FragColor_backup;\n"
          + "     _GLF_gl_FragColor_backup = gl_FragColor;\n"
          + "     gl_FragColor = vec4(0.0);\n"
          + "     {\n"
          + "      gl_FragColor = _GLF_gl_FragColor_backup;\n"
          + "     }\n"
          + "    }\n"
          + "    {\n"
          + "     vec4 _GLF_gl_FragColor_backup;\n"
          + "     _GLF_gl_FragColor_backup = gl_FragColor;\n"
          + "     gl_FragColor = vec4(0.0);\n"
          + "     {\n"
          + "      gl_FragColor = _GLF_gl_FragColor_backup;\n"
          + "     }\n"
          + "    }\n"
          + "}\n";
    String json = "{ }";
    final IRandom generator = new RandomWrapper(0);
    reduce((unused, item) -> generator.nextBoolean(), program, json,
        false, 1000, 0);
  }

  @Test
  public void testVertexShaderIsCarriedThroughReduction() throws Exception {
    String frag = "void main() {\n"
        + "  int shouldBeRemoved = 1;\n"
        + "}\n";
    String vert = "void main() {\n"
        + "  int shouldNotBeRemoved = 1;\n"
        + "}\n";
    String json = "{ }";

    final IFileJudge checkVertexShader = (shaderJobFile, shaderResultFileOutput) -> {
      try {
        String vertexContents = fileOps.getShaderContents(shaderJobFile, ShaderKind.VERTEX);
        return vertexContents.contains("shouldNotBeRemoved");
      } catch (IOException e) {
        throw new FileJudgeException(e);
      }
    };

    final String resultFilesPrefix = reduce(checkVertexShader, frag, Optional.of(vert), json,
        true, 1000, 0);
    CompareAsts.assertEqualAsts("void main() { }", ParseHelper.parse(
        new File(testFolder.getRoot(), resultFilesPrefix + ".frag")));
    CompareAsts.assertEqualAsts(vert, ParseHelper.parse(
        new File(testFolder.getRoot(), resultFilesPrefix + ".vert")));
  }

  @Test
  public void testReductionOfVertexShader() throws Exception {
    String frag = "void main() {\n"
        + "  int shouldNotBeRemoved = 1;\n"
        + "}\n";
    String vert = "void main() {\n"
        + "  int shouldBeRemoved = 1;\n"
        + "}\n";
    String json = "{ }";
    final IRandom generator = new RandomWrapper(0);

    final IFileJudge checkVertexShader = new IFileJudge() {
      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput) throws FileJudgeException {
        try {
          return fileOps
              .getShaderContents(shaderJobFile, ShaderKind.FRAGMENT)
              .contains("shouldNotBeRemoved");
        } catch (IOException e) {
          throw new FileJudgeException(e);
        }
      }
    };

    final String resultFilesPrefix = reduce(checkVertexShader, frag, Optional.of(vert), json,
        true, 1000, 0);
    CompareAsts.assertEqualAsts(frag, ParseHelper.parse(
        new File(testFolder.getRoot(), resultFilesPrefix + ".frag")));
    CompareAsts.assertEqualAsts("void main() { }", ParseHelper.parse(
        new File(testFolder.getRoot(), resultFilesPrefix + ".vert")));
  }

  @Test
  public void testReductionWithUniformBindings() throws Exception {
    final TranslationUnit fragShader = ParseHelper.parse("layout(location = 0) out vec4 "
        + "_GLF_color;"
        + "uniform float a; "
        + "uniform float b;"
        + "void main() {"
        + "  if (a > b) {"
        + "    _GLF_color = vec4(1.0);"
        + "  } else {"
        + "    _GLF_color = vec4(0.0);"
        + "  }"
        + "}");
    final String expected = "void main() { }";
    PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("a", BasicType.FLOAT, Optional.empty(), Arrays.asList(1.0));
    pipelineInfo.addUniform("b", BasicType.FLOAT, Optional.empty(), Arrays.asList(2.0));
    ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo,
        fragShader);
    shaderJob.makeUniformBindings(Optional.empty());

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_300,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        (unused, item) -> true, workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }

  @Test
  public void testReductionWithPushConstantBinding() throws Exception {
    final TranslationUnit fragShader = ParseHelper.parse("layout(location = 0) out vec4 "
        + "_GLF_color;"
        + "uniform float a; "
        + "uniform float b;"
        + "void main() {"
        + "  if (a > b) {"
        + "    _GLF_color = vec4(1.0);"
        + "  } else {"
        + "    _GLF_color = vec4(0.0);"
        + "  }"
        + "}");
    final String expected = "layout(location = 0) out vec4 _GLF_color;\n"
        + "\n"
        + "layout(push_constant) uniform buf_push {\n"
        + " float a;\n"
        + "};\n"
        + "layout(set = 0, binding = 0) uniform buf0 {\n"
        + " float b;\n"
        + "};\n"
        + "void main() {\n"
        + " if(a > b) {\n"
        + "   _GLF_color = vec4(1.0);\n"
        + "  } else {\n"
        + "   _GLF_color = vec4(0.0);\n"
        + "  }\n"
        + "}\n";
    PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("a", BasicType.FLOAT, Optional.empty(), Arrays.asList(1.0));
    pipelineInfo.addUniform("b", BasicType.FLOAT, Optional.empty(), Arrays.asList(2.0));
    ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo,
        fragShader);
    shaderJob.makeUniformBindings(Optional.of("a"));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(false,
        true,
        ShadingLanguageVersion.ESSL_300,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        (unused, item) -> true, workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }


  @Test
  public void testReductionOfUnreferencedUniform() throws Exception {

    // Check that a shader job with metadata for one unreferenced uniform gets
    // reduced to a shader job with no metadata for uniforms.

    final String emptyShader = "void main() { }";
    final TranslationUnit fragShader = ParseHelper.parse(emptyShader);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("a", BasicType.FLOAT, Optional.empty(), Collections.singletonList(1.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo,
        fragShader);
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        (unused, item) -> true, workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    final ShaderJob after = fileOps.readShaderJobFile(new File(testFolder.getRoot(),
        resultsPrefix + ".json"));

    CompareAsts.assertEqualAsts(emptyShader,
        after.getFragmentShader().get());
    assertEquals(0, after.getPipelineInfo().getNumUniforms());

  }

  @Test
  public void testSimplificationOfSwitch() throws Exception {
    final String shader = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(0) {\n"
        + "    case 0:\n"
        + "      mix(0.0, 1.0, 0.0);\n"
        + "      break;\n"
        + "    default:\n"
        + "      1;\n"
        + "  }\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  mix(1.0, 1.0, 1.0);\n"
        + "}\n";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shader));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final IFileJudge usesMixFileJudge =
        new CheckAstFeaturesFileJudge(Collections.singletonList(() -> new CheckAstFeatureVisitor() {
          @Override
          public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
            super.visitFunctionCallExpr(functionCallExpr);
            if (functionCallExpr.getCallee().equals("mix")) {
              trigger();
            }
          }
        }),
            ShaderKind.FRAGMENT,
            fileOps);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        usesMixFileJudge,
        workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }

  @Test
  public void testEliminationOfReturns() throws Exception {
    final String shader = "#version 310 es\n"
        + "int foo() {\n"
        + "  if (false) {\n"
        + "    return 1;\n"
        + "  }\n"
        + "  return 0;\n"
        + "}\n"
        + "void main() {\n"
        + "  if (true) {\n"
        + "    foo();\n"
        + "    return;\n"
        + "  } else {\n"
        + "    return;\n"
        + "  }\n"
        + "  return;\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "int foo() {\n"
        + "  return 1;\n"
        + "}\n"
        + "void main() {\n"
        + "  foo();\n"
        + "}\n";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shader));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final IFileJudge usesFooFileJudge =
        new CheckAstFeaturesFileJudge(Arrays.asList(
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
                    super.visitFunctionCallExpr(functionCallExpr);
                    if (functionCallExpr.getCallee().equals("foo")) {
                      trigger();
                    }
                  }
                },
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitReturnStmt(ReturnStmt returnStmt) {
                super.visitReturnStmt(returnStmt);
                if (returnStmt.hasExpr() && returnStmt.getExpr() instanceof IntConstantExpr
                    && ((IntConstantExpr) returnStmt.getExpr()).getNumericValue() == 1) {
                  trigger();
                }
              }
            }),
            ShaderKind.FRAGMENT,
            fileOps);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        usesFooFileJudge,
        workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }

  @Test
  public void testRemoveIfOrLoopButKeepGuard() throws Exception {
    final String shaderIf = "#version 310 es\n"
        + "void main() {\n"
        + "  if(1 > 0) {\n"
        + "  }\n"
        + "}\n";

    final String shaderWhile = "#version 310 es\n"
        + "void main() {\n"
        + "  while(1 > 0) {\n"
        + "  }\n"
        + "}\n";

    final String shaderDoWhile = "#version 310 es\n"
        + "void main() {\n"
        + "  do {\n"
        + "  } while(1 > 0);\n"
        + "}\n";

    final String shaderFor = "#version 310 es\n"
        + "void main() {\n"
        + "  for (; 1 > 0; ) {\n"
        + "  }\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  1 > 0;\n"
        + "}\n";

    final IFileJudge customFileJudge =
        new CheckAstFeaturesFileJudge(Collections.singletonList(
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitBinaryExpr(BinaryExpr binaryExpr) {
                super.visitBinaryExpr(binaryExpr);
                if (binaryExpr.getOp() == BinOp.GT
                    && binaryExpr.getLhs() instanceof IntConstantExpr
                    && ((IntConstantExpr) binaryExpr.getLhs()).getNumericValue() == 1
                    && binaryExpr.getRhs() instanceof IntConstantExpr
                    && ((IntConstantExpr) binaryExpr.getRhs()).getNumericValue() == 0) {
                  trigger();
                }
              }
            }),
            ShaderKind.FRAGMENT,
            fileOps);

    for (String shader : Arrays.asList(shaderIf, shaderWhile, shaderDoWhile, shaderFor)) {
      final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
          new PipelineInfo(),
          ParseHelper.parse(shader));

      final File workDir = testFolder.getRoot();
      final File tempShaderJobFile = new File(workDir, "temp.json");
      fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

      final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
          true,
          ShadingLanguageVersion.ESSL_310,
          new RandomWrapper(0),
          new IdGenerator()),
          false,
          fileOps,
          customFileJudge,
          workDir)
          .doReduction(shaderJob, "temp", 0, 100);

      CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
          resultsPrefix + ".frag")));
    }

  }

  @Test
  public void testRemoveSwitchButKeepGuard() throws Exception {

    final String shaderSwitch = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(0) {\n"
        + "    case 1:\n"
        + "      break;\n"
        + "  }\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  0;\n"
        + "}\n";

    final IFileJudge customFileJudge =
        new CheckAstFeaturesFileJudge(Collections.singletonList(
            () -> new CheckAstFeatureVisitor() {
              @Override
              public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
                super.visitIntConstantExpr(intConstantExpr);
                if (intConstantExpr.getNumericValue() == 0) {
                  trigger();
                }
              }
            }),
            ShaderKind.FRAGMENT,
            fileOps);

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shaderSwitch));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        customFileJudge,
        workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }

  @Test
  public void testMakingArrayAccessesInBoundsIsValid() throws Exception {

    // Checks that the shaders to which the interestingness test is applied are always valid.  In
    // particular, this is designed to check that the shader with in-bounds array indices that is
    // tried at the start of the reduction process, is valid.

    final String shaderSwitch = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int A[3];\n"
        + "  for (int i = 0; i < 12; i++) {\n"
        + "    A[i] = i;\n"
        + "  }\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  0;\n"
        + "}\n";

    final IFileJudge customFileJudge = new IFileJudge() {
      @Override
      public boolean isInteresting(File shaderJobFile, File shaderResultFileOutput)
          throws FileJudgeException {
        try {
          fileOps.areShadersValid(shaderJobFile, true);
        } catch (IOException | InterruptedException exception) {
          throw new FileJudgeException(exception);
        }
        return true;
      }
    };

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shaderSwitch));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    new ReductionDriver(new ReducerContext(true,
        true,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        customFileJudge,
        workDir)
        .doReduction(shaderJob, "temp", 0, 100);

  }

  private String getPrefix(File tempFile) {
    return FilenameUtils.removeExtension(tempFile.getName());
  }

  @Test
  public void testGranularityAtMinimum() throws Exception {

    final String shader =
        "#version 310 es\n"
            + "void main() {\n"
            + "  int a = 1;\n"
            + "  int b = 2;\n"
            + "  int c = 3;\n"
            + "  int d = 4;\n"
            + "  int e = 5;\n"
            + "  int f = 6;\n"
            + "}\n";

    final String expected =
        "#version 310 es\n"
            + "layout(set = 0, binding = 0) uniform buf0 {"
            + "int _GLF_uniform_int_values[5];"
            + "};"
            + "void main() {\n"
            + "  int a = _GLF_uniform_int_values[0];\n"
            + "  int b = _GLF_uniform_int_values[1];\n"
            + "  int c = _GLF_uniform_int_values[2];\n"
            + "  int d = _GLF_uniform_int_values[3];\n"
            + "  int e = _GLF_uniform_int_values[4];\n"
            + "  int f = 6;\n"
            + "}\n";

    class GranularityJudge implements IFileJudge {

      private int counter;
      private final ShaderJobFileOperations fileOps;

      public GranularityJudge(ShaderJobFileOperations fileOps) {
        this.counter = 0;
        this.fileOps = fileOps;
      }

      @Override
      public boolean isInteresting(
          File shaderJobFile,
          File shaderResultFileOutput
      ) {
        try {
          if (!fileOps.areShadersValid(shaderJobFile, true)) {
            return false;
          }
        } catch (IOException | InterruptedException ex) {
          throw new RuntimeException(ex);
        }
        counter++;

        // Initial state should be interesting but after the first reduction attempt, in which all
        // the literals are replaced, the shader becomes non-interesting. However, if the reduction
        // algorithm is correct, it keeps on running until every literal except one has been
        // replaced.
        if (counter == 2) {
          return false;
        }
        return true;
      }
    }

    final IFileJudge granularityJudge = new GranularityJudge(fileOps);

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shader));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final String resultsPrefix = new ReductionDriver(new ReducerContext(false,
        true,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        granularityJudge,
        workDir,
        true)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));
  }

  @Test
  public void testSimplificationOfTernaryWithArray() throws Exception {
    final String shader = "#version 310 es\n"
        + "void main()\n"
        + "{\n"
        + " int ext_0;\n"
        + " int ext_1[3];\n"
        + " int ext_2[3];\n"
        + " int[3](1, 1, 1)[(all(bvec2(true)) ? (+ abs(ext_1[1])) : (ext_0 & -- ext_2[2]))] |= 1;\n"
        + "}\n";

    final String expected = "#version 310 es\n"
        + "void main()\n"
        + "{\n"
        + " int ext_1[3];\n"
        + " int[3](1, 1, 1)[abs(ext_1[1])] |= 1;\n"
        + "}\n";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(),
        ParseHelper.parse(shader));

    final File workDir = testFolder.getRoot();
    final File tempShaderJobFile = new File(workDir, "temp.json");
    fileOps.writeShaderJobFile(shaderJob, tempShaderJobFile);

    final IFileJudge customJudge = (file, unused) -> {
      try {
        ShaderJob customJudgeShaderJob = fileOps.readShaderJobFile(file);
        assert customJudgeShaderJob.getShaders().size() == 1;
        final TranslationUnit tu = customJudgeShaderJob.getShaders().get(0);
        final String shaderString = tu.getText();
        return shaderString.contains("abs(ext_1[1])") && shaderString.contains(" |= 1");
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    };

    final String resultsPrefix = new ReductionDriver(new ReducerContext(true,
        false,
        ShadingLanguageVersion.ESSL_310,
        new RandomWrapper(0),
        new IdGenerator()),
        false,
        fileOps,
        customJudge,
        workDir)
        .doReduction(shaderJob, "temp", 0, 100);

    CompareAsts.assertEqualAsts(expected, ParseHelper.parse(new File(testFolder.getRoot(),
        resultsPrefix + ".frag")));

  }

}
