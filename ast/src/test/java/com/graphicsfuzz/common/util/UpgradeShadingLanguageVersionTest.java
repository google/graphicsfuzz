/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import org.junit.Test;

public class UpgradeShadingLanguageVersionTest {

  @Test
  public void testFragcoordUpgrade() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "void main() {\n"
        + "  _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_310);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testVaryingUpgrade() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "varying vec4 inp;\n"
        + "void main() {\n"
        + "  gl_FragColor = inp;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "in vec4 inp;\n"
        + "void main() {\n"
        + "  _GLF_color = inp;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_310);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testTextureUpgrade() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  gl_FragColor = texture2D(vec2(0.0,1.0));\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "void main() {\n"
        + "  _GLF_color = texture(vec2(0.0,1.0));\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_310);
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }
}
