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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.DonateLiveCodeTransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GenerateTest {

  private static final String A_VERTEX_SHADER = "#version 300 es\n"
      + "layout(location=0) in highp vec4 a_position;"
      + "float foo(float b) {"
      + "  for (int i = 0; i < 10; i++) {"
      + "    b += float(i);"
      + "  }"
      + "  return b;"
      + "}"
      + ""
      + "void main()"
      + "{\n"
      + "  float x = 0.0;"
      + "  int a;"
      + "  a = 100;"
      + "  while (a > 0) {"
      + "    a = a - 2;"
      + "    x += foo(x);"
      + "  }"
      + "  vec2 iAmAVertexShader;"
      + "}";
  public static final String EMPTY_JSON = "{\n"
      + "}";
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testSynthetic() throws Exception {

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "layout(location=0) out highp vec4 _GLF_color;"
        + "uniform vec2 injectionSwitch;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + "    int r;\n"
        + "    int g;\n"
        + "    int b;\n"
        + "    int a;\n"
        + "    r = 100 * int(injectionSwitch.y);\n"
        + "    g = int(injectionSwitch.x) * int(injectionSwitch.y);\n"
        + "    b = 2 * int(injectionSwitch.x);\n"
        + "    a = g - int(injectionSwitch.x);\n"
        + "    for(\n"
        + "        int i = 0;\n"
        + "        i < 10;\n"
        + "        i ++\n"
        + "    )\n"
        + "        {\n"
        + "            r --;\n"
        + "            g ++;\n"
        + "            b ++;\n"
        + "            a ++;\n"
        + "            for(\n"
        + "                int j = 1;\n"
        + "                j < 10;\n"
        + "                j ++\n"
        + "            )\n"
        + "                {\n"
        + "                    a ++;\n"
        + "                    b ++;\n"
        + "                    g ++;\n"
        + "                    r --;\n"
        + "                }\n"
        + "        }\n"
        + "    float fr;\n"
        + "    float fg;\n"
        + "    float fb;\n"
        + "    float fa;\n"
        + "    fr = float(r / 100);\n"
        + "    fg = float(g / 100);\n"
        + "    fb = float(b / 100);\n"
        + "    fa = float(a / 100);\n"
        + "    _GLF_color = vec4(r, g, b, a);\n"
        + "}\n";

    final String json = "{\n"
        + "  \"injectionSwitch\": {\n"
        + "    \"func\": \"glUniform2f\",\n"
        + "    \"args\": [\n"
        + "      0.0,\n"
        + "      1.0\n"
        + "    ]\n"
        + "  }\n"
        + "}";

    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);

    final File outputDir = temporaryFolder.getRoot();
    final File outputShaderJobFile = new File(outputDir, "output.json");
    final File donors = temporaryFolder.newFolder("donors");

    Generate.mainHelper(new String[]{"--seed", "0",
        shaderJobFile.toString(),
        donors.toString(),
        outputShaderJobFile.toString(),
    });

    fileOps.areShadersValid(outputShaderJobFile, true);

  }

  @Test
  public void testStructDonation() throws Exception {
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String usesStruct = "struct A { int x; }; void main() { { A a = A(1); a.x = 2; } }";
    final File donorsFolder = temporaryFolder.newFolder();
    for (int i = 0; i < 10; i++) {
      final File donor = new File(
          Paths.get(donorsFolder.getAbsolutePath(), "donor" + i + ".frag").toString());
      fileOps.writeStringToFile(donor, usesStruct);
    }
    final String reference = "void main() { ; { ; ; ; }; ; { ; ; ; }; ; ; ; ; ; ; }";
    final TranslationUnit tu = ParseHelper.parse(reference);
    new DonateLiveCodeTransformation(TransformationProbabilities
        .likelyDonateLiveCode()::donateLiveCodeAtStmt,
        donorsFolder, GenerationParams.normal(ShaderKind.FRAGMENT, false, true), false)
        .apply(tu,
            TransformationProbabilities.likelyDonateLiveCode(),
            new RandomWrapper(0),
            GenerationParams.normal(ShaderKind.FRAGMENT, false, true));
  }

  @Test
  public void testFragVertAndUniformsPassedThrough() throws Exception {

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final TranslationUnit fragmentShader = ParseHelper.parse("precision mediump float;\n"
        + "void main()\n"
        + "{\n"
        + "  float iAmAFragmentShader;"
        + "}\n", ShaderKind.FRAGMENT);

    final TranslationUnit vertexShader = ParseHelper.parse("void main()\n"
        + "{\n"
        + "  float iAmAVertexShader;"
        + "}\n", ShaderKind.VERTEX);

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(),
        vertexShader, fragmentShader);

    final File shaderJobFile = temporaryFolder.newFile("shader.json");

    fileOps.writeShaderJobFile(shaderJob, shaderJobFile);

    File outputDir = temporaryFolder.getRoot();

    File outputShaderJobFile = new File(outputDir, "output.json");

    File donors = temporaryFolder.newFolder("donors");

    Generate.mainHelper(new String[]{"--seed", "0",
        shaderJobFile.toString(),
        donors.toString(),
        outputShaderJobFile.toString()
    });

    fileOps.areShadersValid(outputShaderJobFile, true);

    assertTrue(
        fileOps
            .getShaderContents(outputShaderJobFile, ShaderKind.FRAGMENT)
            .contains("iAmAFragmentShader")
    );

    assertTrue(
        fileOps
            .getShaderContents(outputShaderJobFile, ShaderKind.VERTEX)
            .contains("iAmAVertexShader")
    );

  }

  @Test
  public void testValidityOfVertexShaderTransformations() throws Exception {
    testValidityOfVertexShaderTransformations(new ArrayList<>(), 0);
  }

  private void testValidityOfVertexShaderTransformations(List<String> extraArgs, int repeatCount)
      throws IOException, InterruptedException, ParseTimeoutException, ArgumentParserException,
      GlslParserException {

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse(A_VERTEX_SHADER, ShaderKind.VERTEX));

    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(shaderJob, shaderJobFile);

    final File outputDir = temporaryFolder.getRoot();
    final File outputShaderJobFile = new File(outputDir, "output.json");
    final File donors = temporaryFolder.newFolder("donors");

    for (int seed = 0; seed < repeatCount; seed++) {
      final List<String> args = new ArrayList<>();
      args.addAll(
          Arrays.asList(
              "--seed", Integer.toString(seed),
              shaderJobFile.toString(),
              donors.toString(),
              outputShaderJobFile.toString()
          )
      );

      args.addAll(extraArgs);

      Generate.mainHelper(args.toArray(new String[0]));

      assertTrue(
          fileOps
              .getShaderContents(outputShaderJobFile, ShaderKind.VERTEX)
              .contains("iAmAVertexShader")
      );
      fileOps.areShadersValid(outputShaderJobFile, true);
    }

  }

  @Test
  public void testValidityOfVertexShaderJumpTransformations() throws Exception {
    testValidityOfVertexShaderTransformations(Arrays.asList("--enable-only", "add_jump"), 5);
  }

  @Test
  public void testInjectionSwitchAddedByDefault() throws Exception {
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String program = "#version 100\n"
        + "precision mediump float;"
        + "void main() {"
        + " int x = 0;"
        + " for (int i = 0; i < 100; i++) {"
        + "  x = x + i;"
        + " }"
        + "}";
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);

    final File donors = temporaryFolder.newFolder();
    final File output = temporaryFolder.newFile("output.json");

    Generate.mainHelper(new String[] { shaderJobFile.getAbsolutePath(), donors.getAbsolutePath(),
        output.getAbsolutePath(), "--seed", "0" });

    final ShaderJob outputShaderJob = fileOps.readShaderJobFile(output);

    assertTrue(outputShaderJob.getPipelineInfo().hasUniform("injectionSwitch"));

    assertTrue(fileOps.areShadersValid(output, false));

    assertTrue(outputShaderJob.getShaders().get(0).getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof VariablesDeclaration)
        .map(item -> ((VariablesDeclaration) item).getDeclInfos())
        .reduce(new ArrayList<>(), ListConcat::concatenate)
        .stream()
        .map(VariableDeclInfo::getName)
        .anyMatch(item -> item.equals("injectionSwitch")));

  }


  @Test
  public void testNoInjectionSwitchIfDisabled() throws Exception {
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String program = "#version 100\n"
        + "precision highp float;"
        + "void main() {"
        + " int x = 0;"
        + " for (int i = 0; i < 100; i++) {"
        + "  x = x + i;"
        + " }"
        + "}";
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);

    final File donors = temporaryFolder.newFolder();
    final File output = temporaryFolder.newFile("output.json");

    Generate.mainHelper(new String[] { shaderJobFile.getAbsolutePath(), donors.getAbsolutePath(),
        output.getAbsolutePath(), "--no-injection-switch", "--seed", "0" });

    final ShaderJob outputShaderJob = fileOps.readShaderJobFile(output);

    assertFalse(outputShaderJob.getPipelineInfo().hasUniform("injectionSwitch"));

    assertTrue(fileOps.areShadersValid(output, false));

    assertFalse(outputShaderJob.getShaders().get(0).getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof VariablesDeclaration)
        .map(item -> ((VariablesDeclaration) item).getDeclInfos())
        .reduce(new ArrayList<>(), ListConcat::concatenate)
        .stream()
        .map(VariableDeclInfo::getName)
        .anyMatch(item -> item.equals("injectionSwitch")));

  }

  @Test
  public void testBeRobustWhenNoDonors() throws Exception {
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String program = "#version 100\n"
        + "void main() { }";
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);

    final File donors = new File(temporaryFolder.getRoot(), "does_not_exist");
    final File output = temporaryFolder.newFile("output.json");

    try {
      Generate.mainHelper(new String[]{shaderJobFile.getAbsolutePath(), donors.getAbsolutePath(),
          output.getAbsolutePath(), "--seed", "0"});
      fail("An exception should have been thrown.");
    } catch (RuntimeException runtimeException) {
      assertTrue(runtimeException.getMessage().contains("Donors directory"));
      assertTrue(runtimeException.getMessage().contains("does not exist"));
    }
  }

  @Test
  public void testStructUniform() throws Exception {
    // Checks that the generator does not fall over when presented with struct uniforms.
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String program = "#version 320 es\n"
        + "struct S { float x; };"
        + "uniform S myS;"
        + "uniform struct T { int y; } myT;"
        + "void main() {}";
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);
    final File donors = temporaryFolder.newFolder();
    final File output = temporaryFolder.newFile("output.json");

    Generate.mainHelper(new String[] { shaderJobFile.getAbsolutePath(), donors.getAbsolutePath(),
        output.getAbsolutePath(), "--seed", "0" });
  }

  @Test
  public void testPushConstant() throws Exception {
    // Checks that the generator does not fall over when presented with struct uniforms.
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final String program = "#version 460\n"
        + "uniform vec4 u1;"
        + "out vec4 c;"
        + "void main() { c = u1; }";
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    fileOps.writeShaderJobFile(new GlslShaderJob(Optional.empty(), new PipelineInfo(),
            ParseHelper.parse(program, ShaderKind.FRAGMENT)),
        shaderJobFile);
    final File donors = temporaryFolder.newFolder();
    final File output = temporaryFolder.newFile("output.json");

    Generate.mainHelper(new String[] { shaderJobFile.getAbsolutePath(), donors.getAbsolutePath(),
        output.getAbsolutePath(), "--seed", "0", "--vulkan",
        "--no-injection-switch", "--push-constant-probability", "1.0" });

    final ShaderJob outputShaderJob = fileOps.readShaderJobFile(output);

    assertTrue(outputShaderJob.getPipelineInfo().hasUniform("u1"));
    assertTrue(outputShaderJob.getPipelineInfo().isPushConstant("u1"));
  }

}
