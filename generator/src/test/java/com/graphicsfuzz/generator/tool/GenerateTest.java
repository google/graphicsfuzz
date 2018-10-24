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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.donation.DonateLiveCode;
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
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;

public class GenerateTest {

  public static final String A_VERTEX_SHADER = "" +
      "float foo(float b) {" +
      "  for (int i = 0; i < 10; i++) {" +
      "    b += float(i);" +
      "  }" +
      "  return b;" +
      "}" +
      "" +
      "void main()"
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

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    // TODO: Use fileOps more.

    final String program = "uniform vec2 injectionSwitch;\n"
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
        + "    gl_FragColor = vec4(r, g, b, a);\n"
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

    File shaderFile = temporaryFolder.newFile("shader.frag");
    File jsonFile = temporaryFolder.newFile("shader.json");
    BufferedWriter bw = new BufferedWriter(new FileWriter(shaderFile));
    bw.write(program);
    bw.close();
    bw = new BufferedWriter(new FileWriter(jsonFile));
    bw.write(json);
    bw.close();

    File outputDir = temporaryFolder.getRoot();

    File outputShaderJobFile = new File(outputDir, "output.json");

    File donors = temporaryFolder.newFolder("donors");

    Generate.mainHelper(new String[]{"--seed", "0",
        jsonFile.toString(),
        donors.toString(),
        "100",
        outputShaderJobFile.toString(),
    });

    fileOps.areShadersValid(outputShaderJobFile, true);

  }

  @Test
  public void testStructDonation() throws Exception {
    final String usesStruct = "struct A { int x; }; void main() { { A a = A(1); a.x = 2; } }";
    File donorsFolder = temporaryFolder.newFolder();
    for (int i = 0; i < 10; i++) {
      File donor = new File(
          Paths.get(donorsFolder.getAbsolutePath(), "donor" + i + ".frag").toString());
      BufferedWriter bw = new BufferedWriter(new FileWriter(donor));
      bw.write(usesStruct);
      bw.close();
    }
    String reference = "void main() { ; { ; ; ; }; ; { ; ; ; }; ; ; ; ; ; ; }";
    TranslationUnit tu = ParseHelper.parse(reference, false);
    new DonateLiveCode(TransformationProbabilities.likelyDonateLiveCode()::donateLiveCodeAtStmt,
        donorsFolder, GenerationParams.normal(ShaderKind.FRAGMENT), false)
        .apply(tu,
            TransformationProbabilities.likelyDonateLiveCode(),
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0),
            GenerationParams.normal(ShaderKind.FRAGMENT));
  }

  @Test
  public void testFragVertAndUniformsPassedThrough() throws Exception {

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    // TODO: Use fileOps more.

    final String dummyFragment = "void main()\n"
        + "{\n"
        + "  float iAmAFragmentShader;"
        + "}\n";

    final String dummyVertex = "void main()\n"
        + "{\n"
        + "  float iAmAVertexShader;"
        + "}\n";

    final String json = "{\n"
        + "}";

    File fragmentShaderFile = temporaryFolder.newFile("shader.frag");
    File vertexShaderFile = temporaryFolder.newFile("shader.vert");
    File jsonFile = temporaryFolder.newFile("shader.json");
    BufferedWriter bw = new BufferedWriter(new FileWriter(fragmentShaderFile));
    bw.write(dummyFragment);
    bw.close();
    bw = new BufferedWriter(new FileWriter(vertexShaderFile));
    bw.write(dummyVertex);
    bw.close();
    bw = new BufferedWriter(new FileWriter(jsonFile));
    bw.write(json);
    bw.close();

    File outputDir = temporaryFolder.getRoot();

    File outputShaderJobFile = new File(outputDir, "output.json");

    File donors = temporaryFolder.newFolder("donors");

    Generate.mainHelper(new String[]{"--seed", "0",
        jsonFile.toString(),
        donors.toString(),
        "100",
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

  @Test
  public void testValidityOfVertexShaderJumpTransformations() throws Exception {
    testValidityOfVertexShaderTransformations(Arrays.asList("--enable_only", "jump"), 5);
  }

  private void testValidityOfVertexShaderTransformations(List<String> extraArgs, int repeatCount) throws IOException, InterruptedException, ParseTimeoutException, ArgumentParserException {

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    // TODO: Use fileOps more.

    File vertexShaderFile = temporaryFolder.newFile("shader.vert");
    File jsonFile = temporaryFolder.newFile("shader.json");
    BufferedWriter bw = new BufferedWriter(new FileWriter(vertexShaderFile));
    bw.write(A_VERTEX_SHADER);
    bw.close();
    bw = new BufferedWriter(new FileWriter(jsonFile));
    bw.write(EMPTY_JSON);
    bw.close();

    final File outputDir = temporaryFolder.getRoot();
    final File outputShaderJobFile = new File(outputDir, "output.json");
    final File donors = temporaryFolder.newFolder("donors");

    for (int seed = 0; seed < repeatCount; seed++) {
      final List<String> args = new ArrayList<>();
      args.addAll(
          Arrays.asList(
              "--seed", Integer.toString(seed),
              jsonFile.toString(),
              donors.toString(),
              "300 es",
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


}