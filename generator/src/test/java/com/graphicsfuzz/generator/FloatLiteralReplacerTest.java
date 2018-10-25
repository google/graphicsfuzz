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
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.util.TestingHelpers;
import org.junit.Test;

public class FloatLiteralReplacerTest {

  @Test
  public void testReplace() throws Exception {
    final String program = ""
        + "float foo(vec3 p, vec2 t) {"
        + "  vec2 q = vec2(0.0);"
        + "  return 0.0;"
        + "}"
        + "void main() { }";

    final TranslationUnit tu = ParseHelper.parse(program);

    final UniformsInfo uniformsInfo = new UniformsInfo();
    FloatLiteralReplacer.replace(tu, uniformsInfo, ShadingLanguageVersion.ESSL_100);

    final String expectedProgram = ""
        + "uniform float _FLOAT_CONST[1];"
        + "float foo(vec3 p, vec2 t) {"
        + "  vec2 q = vec2(_FLOAT_CONST[0]);"
        + "  return _FLOAT_CONST[0];"
        + "}"
        + "void main() { }";

    TestingHelpers.assertShadersMatch(expectedProgram, tu);

  }

  @Test
  public void testNothingToReplace() throws Exception {
    final String program = ""
        + "int foo(vec3 p, vec2 t) {"
        + "  ivec2 q = ivec2(0);"
        + "  return 0;"
        + "}"
        + "void main() { }";

    final TranslationUnit tu = ParseHelper.parse(program);

    final UniformsInfo uniformsInfo = new UniformsInfo();
    FloatLiteralReplacer.replace(tu, uniformsInfo, ShadingLanguageVersion.GLSL_130);

    final String expectedProgram = ""
        + "int foo(vec3 p, vec2 t) {"
        + "  ivec2 q = ivec2(0);"
        + "  return 0;"
        + "}"
        + "void main() { }";

    TestingHelpers.assertShadersMatch(expectedProgram, tu);

  }


}