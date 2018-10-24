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
import java.io.IOException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Test;

public class ObfuscatorTest {

  @Test
  public void testSimpleObfuscate() throws Exception {

    final String original = "void bar();\n"
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

    final String expected = "void f0();\n"
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
    final String original = "int x;\n"
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

    final String expected = "int v0;\n"
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

  private void check(String original, String expected) throws IOException, ParseTimeoutException {
    TranslationUnit tu = ParseHelper.parse(original, false);
    IRandom generator = new ZeroCannedRandom();
    ImmutablePair<TranslationUnit, UniformsInfo> obfuscated =
          Obfuscator.obfuscate(tu, new UniformsInfo(), generator, ShadingLanguageVersion.ESSL_100);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(obfuscated.getLeft()));
  }

}