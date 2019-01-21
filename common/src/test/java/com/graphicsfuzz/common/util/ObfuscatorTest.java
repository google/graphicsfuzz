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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;

public class ObfuscatorTest {

  @Test
  public void testSimpleObfuscate() throws Exception {

    final String original = "#version 100\n"
          + "void bar();\n"
          + "\n"
          + "void foo() {\n"
          + "  sin(1.2); bar();\n"
          + "}\n"
          + "\n"
          + "void bar() {\n"
          + "\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "  bar();\n"
          + "}\n";

    final String expected = "#version 100\n"
          + "void f0();\n"
          + "\n"
          + "void f1() {\n"
          + "  sin(1.2); f0();\n"
          + "}\n"
          + "\n"
          + "void f0() {\n"
          + "\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "  f0();\n"
          + "}\n";

    check(original, expected);

  }

  @Test
  public void testObfuscateWithNesting() throws Exception {
    final String original = "#version 100\n"
          + "int x;\n"
          + "int y;\n"
          + "\n"
          + "void foo(int x, int y) {\n"
          + "  x = 2;\n"
          + "  y = 3;\n"
          + "  {\n"
          + "    int x;\n"
          + "    int y;\n"
          + "    x = x + 2;\n"
          + "    y = y + x;\n"
          + "  }\n"
          + "  x = 4;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "  x = y + 4;\n"
          + "  {\n"
          + "    int y = x;\n"
          + "    int x = y;\n"
          + "    y = 1;\n"
          + "    x = 2;\n"
          + "    foo(x, y);\n"
          + "  }\n"
          + "}\n";

    final String expected = "#version 100\n"
          + "int v0;\n"
          + "int v1;\n"
          + "\n"
          + "void f2(int v3, int v4) {\n"
          + "  v3 = 2;\n"
          + "  v4 = 3;\n"
          + "  {\n"
          + "    int v5;\n"
          + "    int v6;\n"
          + "    v5 = v5 + 2;\n"
          + "    v6 = v6 + v5;\n"
          + "  }\n"
          + "  v3 = 4;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "  v0 = v1 + 4;\n"
          + "  {\n"
          + "    int v7 = v0;\n"
          + "    int v8 = v7;\n"
          + "    v7 = 1;\n"
          + "    v8 = 2;\n"
          + "    f2(v8, v7);\n"
          + "  }\n"
          + "}\n";

    check(original, expected);

  }

  private void check(String original, String expected) throws IOException, ParseTimeoutException,
      InterruptedException {
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new UniformsInfo(),
        ParseHelper.parse(original));
    final IRandom generator = new ZeroCannedRandom();
    final ShaderJob obfuscated =
          Obfuscator.obfuscate(shaderJob, generator);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(obfuscated.getShaders().get(0)));
  }

  @Test
  public void testVertAndFrag() throws Exception {
    final String originalVert = "#version 100\n"
        + "uniform float f;\n"
        + "uniform float g;\n"
        + "uniform int h;\n"
        + "void main() {\n"
        + " float b = f + g + float(h);\n"
        + "}\n";

    final String originalFrag = "#version 100\n"
        + "uniform float m;\n"
        + "uniform float f;\n"
        + "uniform int t;\n"
        + "uniform float g;\n"
        + "void main() {\n"
        + " float b = m + f + float(t) + g;\n"
        + "}\n";

    final String originalUniforms = "{\n" +
        "  \"f\": {\n" +
        "    \"args\": [\n" +
        "      3.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"g\": {\n" +
        "    \"args\": [\n" +
        "      2.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"h\": {\n" +
        "    \"args\": [\n" +
        "      10\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1i\"\n" +
        "  }, \n" +
        "  \"m\": {\n" +
        "    \"args\": [\n" +
        "      20.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"t\": {\n" +
        "    \"args\": [\n" +
        "      3\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1i\"\n" +
        "  } \n" +
        "}";

    final String expectedVert = "#version 100\n"
        + "uniform float v0;\n"
        + "uniform float v1;\n"
        + "uniform int v2;\n"
        + "void main() {\n"
        + " float v3 = v0 + v1 + float(v2);\n"
        + "}\n";

    final String expectedFrag = "#version 100\n"
        + "uniform float v4;\n"
        + "uniform float v0;\n"
        + "uniform int v5;\n"
        + "uniform float v1;\n"
        + "void main() {\n"
        + " float v6 = v4 + v0 + float(v5) + v1;\n"
        + "}\n";

    final String expectedUniforms = "{\n" +
        "  \"v0\": {\n" +
        "    \"args\": [\n" +
        "      3.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"v1\": {\n" +
        "    \"args\": [\n" +
        "      2.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"v2\": {\n" +
        "    \"args\": [\n" +
        "      10\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1i\"\n" +
        "  }, \n" +
        "  \"v4\": {\n" +
        "    \"args\": [\n" +
        "      20.0\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1f\"\n" +
        "  }, \n" +
        "  \"v5\": {\n" +
        "    \"args\": [\n" +
        "      3\n" +
        "    ], \n" +
        "    \"func\": \"glUniform1i\"\n" +
        "  } \n" +
        "}";

    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        new UniformsInfo(originalUniforms),
        ParseHelper.parse(originalVert, ShaderKind.VERTEX),
        ParseHelper.parse(originalFrag, ShaderKind.FRAGMENT));
    final IRandom generator = new ZeroCannedRandom();
    final ShaderJob obfuscated =
        Obfuscator.obfuscate(shaderJob, generator);
    CompareAsts.assertEqualAsts(expectedVert, obfuscated.getShaders().get(0));
    CompareAsts.assertEqualAsts(expectedFrag, obfuscated.getShaders().get(1));
    assertEquals(new UniformsInfo(expectedUniforms).toString(), obfuscated.getUniformsInfo().toString());
  }

}
