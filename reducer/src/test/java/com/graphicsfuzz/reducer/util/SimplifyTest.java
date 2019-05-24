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

package com.graphicsfuzz.reducer.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class SimplifyTest {

  @Test
  public void testIfParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + "  if (_GLF_DEAD(_GLF_FALSE(false, false))) {"
        + "  }"
        + "}");
    final String expected = "void main() {"
        + "  if (false) {"
        + "  }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testWhileParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + "  while(_GLF_WRAPPED_LOOP(_GLF_FALSE(false ,_GLF_DEAD(_GLF_FALSE(false, false))))) {"
        + "  }"
        + "}");
    final String expected = "void main() {"
        + "  while (false) {"
        + "  }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testForParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + " for ("
        + "   int i = int(_GLF_ONE(1.0, _GLF_IDENTITY(1.0, 1.0)));"
        + "   i > _GLF_IDENTITY(1, _GLF_FUZZED(1));"
        + "   i++)"
        + "   { }"
        + " }"
        + "}");
    final String expected = "void main() {"
        + " for (int i = int(1.0); i > 1; i++)"
        + "   { }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testSwitchParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + " switch(_GLF_SWITCH(_GLF_ZERO(0, _GLF_IDENTITY(0, 0))))"
        + "   {"
        + "   }"
        + "}");
    final String expected = "void main() {"
        + " switch(0)"
        + "   {"
        + "   }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testDoWhileParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + " do { } while (_GLF_WRAPPED_LOOP(_GLF_DEAD(_GLF_FALSE(false, false))));"
        + "}");
    final String expected = "void main() {"
        + " do { } while (false);"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testMacroBlockRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + " _GLF_FUZZED(1);"
        + " _GLF_FUZZED(_GLF_IDENTITY(1, 1));"
        + " int a = _GLF_IDENTITY(_GLF_FUZZED(1), 1);"
        + "}"
    );
    final String expected = "void main() {"
        + " 1;"
        + " 1;"
        + " int a = 1;"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

  @Test
  public void testIdentityNotNestedRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + " if(_GLF_IDENTITY(true, true)) {}"
        + " int x = _GLF_IDENTITY(1, 1);"
        + " x = _GLF_IDENTITY(1, 1);"
        + " _GLF_IDENTITY(1, 1);"
        + "}"
    );
    final String expected = "void main() {"
        + " if(true) {}"
        + " int x = 1;"
        + " x = 1;"
        + " 1;"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }
}
