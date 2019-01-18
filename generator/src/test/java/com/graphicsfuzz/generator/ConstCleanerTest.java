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

package com.graphicsfuzz.generator;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class ConstCleanerTest {

  @Test
  public void testClean() throws Exception {
    final String program =
        "void main() {"
            + "  int x;"
            + "  const int y = x + 2;"
            + "}";

    final String expectedProgram =
        "void main() {"
            + "  int x;"
            + "  int y = x + 2;"
            + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.ESSL_100);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanLarger() throws Exception {
    final String program =
        "void main() {"
            + "  int x;"
            + "  const int y = x + 2;"
            + "  const int z = 2 + y;"
            + "}";

    final String expectedProgram =
        "void main() {"
            + "  int x;"
            + "  int y = x + 2;"
            + "  int z = 2 + y;"
            + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.GLSL_440);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanWithGlobals() throws Exception {
    final String program = ""
            + "int g1;"
            + "const int g2 = g1;"
            + "void main() {"
            + "}";

    final String expectedProgram = ""
            + "int g1;"
            + "int g2;"
            + "void main() {"
            + "  g2 = g1;"
            + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.ESSL_100);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanWithGlobalsGLSL100() throws Exception {
    final String program = ""
        + "int g1;"
        + "int g2 = g1;"
        + "void main() {"
        + "}";

    final String expectedProgram = ""
        + "int g1;"
        + "int g2;"
        + "void main() {"
        + "  g2 = g1;"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.ESSL_100);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanWithGlobalsGLSL440() throws Exception {
    final String program = ""
        + "int g1;"
        + "int g2 = g1;"
        + "void main() {"
        + "}";

    final String expectedProgram = ""
        + "int g1;"
        + "int g2 = g1;"
        + "void main() {"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.GLSL_440);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanWithGlobalsGLSL100MultipleDeclarations() throws Exception {
    final String program = ""
        + "int g1;"
        + "int g2 = g1, g3 = g1, g4 = 5;"
        + "void main() {"
        + "}";

    final String expectedProgram = ""
        + "int g1;"
        + "int g2, g3, g4;"
        + "void main() {"
        + "  g2 = g1;"
        + "  g3 = g1;"
        + "  g4 = 5;"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.ESSL_100);

    CompareAsts.assertEqualAsts(expectedProgram, tu);

  }

  @Test
  public void testCleanWithGlobalsGLSL100Misc() throws Exception {
    final String program = ""
        + "uniform float _FLOAT_CONST[1];"
        + "float foo(vec3 p, vec2 t) {"
        + "  vec2 q = vec2(_FLOAT_CONST[0]);"
        + "  return _FLOAT_CONST[0];"
        + "}"
        + "void main() { }";

    final TranslationUnit tu = ParseHelper.parse(program);

    ConstCleaner.clean(tu, ShadingLanguageVersion.ESSL_100);

    CompareAsts.assertEqualAsts(program, tu);

  }

}
