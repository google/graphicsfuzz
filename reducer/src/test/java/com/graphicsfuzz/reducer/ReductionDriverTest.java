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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.EmitShaderHelper;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.reducer.glslreducers.GlslReductionState;
import com.graphicsfuzz.reducer.glslreducers.GlslReductionStateFileWriter;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReductionDriverTest {

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

    TranslationUnit tu = ParseHelper.parse(tempFile, false);

    GlslReductionState state = new GlslReductionState(Optional.of(tu));

    IFileJudge pessimist = new IFileJudge() {

      // Says interesting first time, and uninteresting thereafter.

      boolean first = true;

      @Override
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
        if (first) {
          first = false;
          return true;
        }
        return false;
      }
    };

    List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(tu,
          new ReductionOpportunityContext(false, version, generator, new IdGenerator()));
    assertEquals(2, ops.size());

    new ReductionDriver(
        new ReductionOpportunityContext(false, version, generator, null),
        false, state).doReduction(getPrefix(tempFile), 0, new GlslReductionStateFileWriter(version),
        pessimist, testFolder.getRoot(), -1);

  }

  @Test
  public void checkUniformIsRemoved() throws Exception {

    File tempFile = testFolder.newFile("temp.frag");
    File tempJsonFile = testFolder.newFile("temp.json");

    String program = "uniform vec3 unused; void main() { }";

    BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
    bw.write(program);
    bw.close();

    BufferedWriter bwJson = new BufferedWriter(new FileWriter(tempJsonFile));
    bwJson.write("{ \"unused\": { \"func\": \"glUniform3f\", \"args\": [ 1.0, 1.0, 1.0 ] } }");
    bwJson.close();

    ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    IRandom generator = new RandomWrapper(0);

    TranslationUnit tu = ParseHelper.parse(tempFile, false);

    GlslReductionState state = new GlslReductionState(Optional.of(tu));

    IFileJudge optimist = (item -> true);

    String finalFilePrefix = new ReductionDriver(new ReductionOpportunityContext(false, version, generator, null), false, state)
        .doReduction(getPrefix(tempFile), 0, new GlslReductionStateFileWriter(version),
        optimist, testFolder.getRoot(), -1);

    UniformsInfo uniformsInfo = new UniformsInfo(
        new File(finalFilePrefix + ".json"));

    assertTrue(uniformsInfo.getUniformNames().isEmpty());

  }

  @Test
  public void checkUnsuccessfulReductionLeavesTrace() throws Exception {
    final String finalFilePrefix = reduce((item -> false),
          "void main() { }", "{ }",
          false,
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
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
        count++;
        return count == 3;
      }
    };

    final String finalFilePrefix = reduce(judge, "void main() { }", "{ }",
          false, false);
    assertNotNull(finalFilePrefix);
  }

  @Test
  public void testInitialStateNotCheckedExcessively() throws Exception {
    IFileJudge judge = new IFileJudge() {
      private int count = 0;
      @Override
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
        count++;
        return count == 10;
      }
    };

    final String finalFilePrefix = reduce((item -> false), "void main() { }", "{ }",
          false, false);
    assertNull(finalFilePrefix);
  }

  private String reduce(IFileJudge judge, String program, String jsonString,
        boolean stripHeader,
        boolean reduceEverywhere)
        throws IOException, ParseTimeoutException {
    return reduce(judge, program, jsonString, stripHeader, reduceEverywhere,
          -1, 0);
  }

  private String reduce(IFileJudge judge, String fragmentShader, String jsonString,
      boolean stripHeader,
      boolean reduceEverywhere,
      int stepLimit,
      int seed)
      throws IOException, ParseTimeoutException {
    return reduce(judge, fragmentShader, Optional.empty(), jsonString,
        stripHeader,
        reduceEverywhere,
        stepLimit,
        seed);
  }

  private String reduce(IFileJudge judge,
        String fragmentShader,
        Optional<String> vertexShader,
        String jsonString,
        boolean stripHeader,
        boolean reduceEverywhere,
        int stepLimit,
        int seed)
        throws IOException, ParseTimeoutException {
    File tempFragmentShaderFile = testFolder.newFile("temp.frag");
    Optional<File> tempVertexShaderFile = vertexShader.isPresent() ?
        Optional.of(testFolder.newFile("temp.vert")) : Optional.empty();
    File tempJsonFile = testFolder.newFile("temp.json");

    BufferedWriter bwFrag = new BufferedWriter(new FileWriter(tempFragmentShaderFile));
    bwFrag.write(fragmentShader);
    bwFrag.close();

    if (vertexShader.isPresent()) {
      BufferedWriter bwVert = new BufferedWriter(new FileWriter(tempVertexShaderFile.get()));
      bwVert.write(vertexShader.get());
      bwVert.close();
    }

    BufferedWriter bwJson = new BufferedWriter(new FileWriter(tempJsonFile));
    bwJson.write(jsonString);
    bwJson.close();

    ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    IRandom generator = new RandomWrapper(seed);

    TranslationUnit tuFrag = Helper.parse(tempFragmentShaderFile, stripHeader);
    Optional<TranslationUnit> tuVert = vertexShader.isPresent()
        ? Optional.of(Helper.parse(tempVertexShaderFile.get(), stripHeader))
        : Optional.empty();

    GlslReductionState state = new GlslReductionState(Optional.of(tuFrag), tuVert);

    return new ReductionDriver(new ReductionOpportunityContext(reduceEverywhere, version, generator, new IdGenerator()), false, state)
        .doReduction(getPrefix(tempFragmentShaderFile), 0, new GlslReductionStateFileWriter(version),
          judge, testFolder.getRoot(), stepLimit);
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

    final TranslationUnit tu = ParseHelper.parse(tempFile, false);

    GlslReductionState state = new GlslReductionState(Optional.of(tu));

    IFileJudge referencesSinCosAnd3 = filesPrefix -> {
        try {
          final String contents = FileUtils.readFileToString(
              new File(filesPrefix + ".frag"), StandardCharsets.UTF_8);
          return contents.contains("float GLF_live3_x = 3.0 + sin(7.0);") && contents.contains("float GLF_live3_y = GLF_live3_x + cos(8.0);")
                      || contents.contains("float GLF_live3_x = 3.0 + sin(7.0);") && contents.contains("float GLF_live3_y = (3.0 + sin(7.0)) + cos(8.0);")
                      || contents.contains("float GLF_live3_x = 3.0;") && contents.contains("float GLF_live3_y = (3.0 + sin(7.0)) + cos(8.0);")
                      || contents.contains("float GLF_live3_x = 3.0;") && contents.contains("float GLF_live3_y = (sin(7.0)) + cos(8.0);")
                      || contents.contains("float GLF_live3_x = 3.0;") && contents.contains("float GLF_live3_y = sin(7.0) + cos(8.0);");
        } catch (IOException e) {
          return false;
        }
      };

    final String reducedFilesPrefix = new ReductionDriver(new ReductionOpportunityContext(false, version, generator, null), false, state)
        .doReduction(getPrefix(tempFile), 0, new GlslReductionStateFileWriter(version),
          referencesSinCosAnd3, testFolder.getRoot(), -1);

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(
              new File(reducedFilesPrefix + ".frag"), true)));

  }

  @Test
  public void testLiveGLFragColorWriteOpportunity() throws Exception {
    IFileJudge judge = filesPrefix -> {
      try {
        return FileUtils.readFileToString(
            new File(filesPrefix + ".frag"), StandardCharsets.UTF_8).contains("if(true)");
      } catch (IOException exception) {
        return false;
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
            + "}",
    "{ }", false, false);
    final String expected = "void main() {"
          + "   if(true) {"
          + "   }"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          Helper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(
                Helper.parse(new File(resultFilesPrefix + ".frag"), true)));
  }

  @Test
  public void testInlineReduceEverywhere() throws Exception {
    final IFileJudge judge = new CheckAstFeaturesFileJudge(
          Arrays.asList(
                () -> new CheckAstFeatureVisitor() {
                  @Override
                  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
                    super.visitFunctionCallExpr(functionCallExpr);
                    if (functionCallExpr.getCallee().equals("sin")) {
                      trigger();
                    }
                  }
                }), ShaderKind.FRAGMENT);

    final String resultFilesPrefix = reduce(judge,
          EmitShaderHelper.getDefinesString(ShadingLanguageVersion.ESSL_100,
                ShaderKind.FRAGMENT,
                () -> new StringBuilder(),
                Optional.empty())
                .toString()
                + ""
                + "float foo(float a) { return sin(a); }"
                + "void main() {"
                + "  float f = foo(42.0);"
                + "}",
          "{ }", true, true);
    final String expected = "void main() {"
          + "   sin(1.0);"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          Helper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(
                Helper.parse(new File(resultFilesPrefix + ".frag"), true)));
  }

  @Test
  public void testInlineLiveCode() throws Exception {
    final IFileJudge judge = new CheckAstFeaturesFileJudge(
          Arrays.asList(
                () -> new CheckAstFeatureVisitor() {
                  @Override
                  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
                    super.visitFunctionCallExpr(functionCallExpr);
                    if (functionCallExpr.getCallee().equals("sin")) {
                      trigger();
                    }
                  }
                }), ShaderKind.FRAGMENT);

    final String resultFilesPrefix = reduce(judge,
          EmitShaderHelper.getDefinesString(ShadingLanguageVersion.ESSL_100,
                ShaderKind.FRAGMENT,
                () -> new StringBuilder(), Optional.empty())
                .toString()
                + ""
                + "vec3 GLF_live3intersects(vec3 GLF_live3src, vec3 GLF_live3direction) {"
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
          "{ }", true, false);
    final String expected = "void main() {"
          + "   sin(vec3(1.0));"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
          Helper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(
                Helper.parse(new File(resultFilesPrefix + ".frag"), true)));
  }

  @Test
  public void testIncompleteReductionEndsCorrectly() throws Exception {
    final IFileJudge interestingFirstTime = new IFileJudge() {
      private boolean first = true;

      @Override
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
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

    final String resultFilesPrefix = reduce(interestingFirstTime, program, json, false, true, 2, 0);

    assertEquals("temp_reduced_final", FilenameUtils.getBaseName(resultFilesPrefix));

    assertTrue(new File(testFolder.getRoot(), Constants.REDUCTION_INCOMPLETE).exists());

    CompareAsts.assertEqualAsts(program, ParseHelper.parse(new File(resultFilesPrefix + ".frag"), true));

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
    final String resultFilesPrefix = reduce(item -> generator.nextBoolean(), program, json,
            false, false, 1000, 0);
  }

  @Test
  public void testVertexShaderIsCarriedThroughReduction() throws Exception {
    String frag = "void main() {"
        + "  int shouldBeRemoved;"
        + "}";
    String vert = "void main() {"
        + "  int shouldNotBeRemoved;"
        + "}";
    String json = "{ }";
    final IRandom generator = new RandomWrapper(0);

    final IFileJudge checkVertexShader = new IFileJudge() {
      @Override
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
        try {
          return FileUtils.readFileToString(
                new File(filesPrefix + ".vert"),
              StandardCharsets.UTF_8)
              .contains("shouldNotBeRemoved");
        } catch (IOException e) {
          throw new FileJudgeException(e);
        }
      }
    };

    final String resultFilesPrefix = reduce(checkVertexShader, frag, Optional.of(vert), json,
        false, true, 1000, 0);
    CompareAsts.assertEqualAsts("void main() { }", Helper.parse(
        new File(resultFilesPrefix + ".frag"), true));
    CompareAsts.assertEqualAsts(vert, Helper.parse(
        new File(resultFilesPrefix + ".vert"), true));
  }

  @Test
  public void testReductionOfVertexShader() throws Exception {
    String frag = "void main() {"
        + "  int shouldNotBeRemoved;"
        + "}";
    String vert = "void main() {"
        + "  int shouldBeRemoved;"
        + "}";
    String json = "{ }";
    final IRandom generator = new RandomWrapper(0);

    final IFileJudge checkVertexShader = new IFileJudge() {
      @Override
      public boolean isInteresting(String filesPrefix) throws FileJudgeException {
        try {
          return FileUtils.readFileToString(
              new File(filesPrefix + ".frag"),
              StandardCharsets.UTF_8)
              .contains("shouldNotBeRemoved");
        } catch (IOException e) {
          throw new FileJudgeException(e);
        }
      }
    };

    final String resultFilesPrefix = reduce(checkVertexShader, frag, Optional.of(vert), json,
        false, true, 1000, 0);
    CompareAsts.assertEqualAsts(frag, Helper.parse(
        new File(resultFilesPrefix + ".frag"), true));
    CompareAsts.assertEqualAsts("void main() { }", Helper.parse(
        new File(resultFilesPrefix + ".vert"), true));
  }

  private String getPrefix(File tempFile) {
    return FilenameUtils.removeExtension(tempFile.getAbsolutePath());
  }

}