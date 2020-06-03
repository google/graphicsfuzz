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
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "void main() {\n"
        + "  _GLF_color = vec4(1.0, 0.0, 0.0, 1.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testVaryingUpgrade() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "varying vec4 inp;\n"
        + "void main() {\n"
        + "  gl_FragColor = inp;\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "in vec4 inp_;\n"
        + "void main() {\n"
        + "  _GLF_color = inp_;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testTextureUpgrade() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "void main() {\n"
        + "  gl_FragColor = texture2D(vec2(0.0,1.0));\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "void main() {\n"
        + "  _GLF_color = texture(vec2(0.0,1.0));\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testGlobalInitializer() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "vec2 foo = vec2(0.0,1.0);\n"
        + "const float notpi = 3.2;\n"
        + "vec2 bar = vec2(1.0,0.0);\n"
        + "vec2 rot(vec2 p) {\n"
        + "  vec2 r = vec2(p.y, p.x);\n"
        + "  return r;\n"
        + "}\n"
        + "void main() {\n"
        + "  gl_FragColor = texture2D(rot(foo));\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "vec2 foo_;\n"
        + "const float notpi_ = 3.2;\n"
        + "vec2 bar_;\n"
        + "vec2 rot_(vec2 p_) {\n"
        + "  vec2 r_ = vec2(p_.y, p_.x);\n"
        + "  return r_;\n"
        + "}\n"
        + "void main() {\n"
        + "  foo_ = vec2(0.0,1.0);\n"
        + "  bar_ = vec2(1.0,0.0);\n"
        + "  _GLF_color = texture(rot_(foo_));\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testBuiltinRename() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "vec2 texture;\n"
        + "float sin(float v) {\n"
        + "  return v - (v * v * v) / 6.0 + (v * v * v * v * v) / 120.0;\n"
        + "}\n"
        + "void main() {\n"
        + "  texture = vec2(sin(0.0), 1.0);"
        + "  gl_FragColor = texture2D(texture);\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "vec2 texture_;\n"
        + "float sin_(float v_) {\n"
        + "  return v_ - (v_ * v_ * v_) / 6.0 + (v_ * v_ * v_ * v_ * v_) / 120.0;\n"
        + "}\n"
        + "void main() {\n"
        + "  texture_ = vec2(sin_(0.0), 1.0);"
        + "  _GLF_color = texture(texture_);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoRename() throws Exception {
    final String shader = "#version 100\n"
        + "precision mediump float;\n"
        + "vec2 texture;\n"
        + "float sin(float v) {\n"
        + "  return v - (v * v * v) / 6.0 + (v * v * v * v * v) / 120.0;\n"
        + "}\n"
        + "void main() {\n"
        + "  texture = vec2(sin(0.0), 1.0);"
        + "  gl_FragColor = texture2D(texture);\n"
        + "}\n";
    final String expected = "#version 320 es\n"
        + "precision mediump float;\n"
        + "layout(location = 0) out vec4 _GLF_color;\n"
        + "vec2 texture;\n"
        + "float sin(float v) {\n"
        + "  return v - (v * v * v) / 6.0 + (v * v * v * v * v) / 120.0;\n"
        + "}\n"
        + "void main() {\n"
        + "  texture = vec2(sin(0.0), 1.0);"
        + "  _GLF_color = texture(texture);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    UpgradeShadingLanguageVersion.upgrade(tu, ShadingLanguageVersion.ESSL_320, false);
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
