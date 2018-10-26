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

import com.graphicsfuzz.common.ast.TranslationUnit;
import org.junit.Test;

public class StripUnusedGlobalsTest {

  @Test
  public void testStripUnusedGlobals() throws Exception {
    final String original = ""
          + "int a = 0, b = a, c;"
          + "float d = 2.0, e[4];"
          + "void baz() {"
          + "  d = 2;"
          + "}"
          + "int x, y = c;"
          + "void foo() {"
          + "  d = float(x);"
          + "}"
          + "float t;"
          + "float t2 = d;"
          + "float l, m;"
          + "void main() {"
          + "}";
    final String expected = ""
          + "int a = 0, c;"
          + "float d = 2.0;"
          + "void baz() {"
          + "  d = 2;"
          + "}"
          + "int x;"
          + "void foo() {"
          + "  d = float(x);"
          + "}"
          + "void main() {"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(original);
    StripUnusedGlobals.strip(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoNotStripStruct() throws Exception {
    final String original = ""
        + "struct S { int a; };"
        + "void main() {"
        + "  S myS;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(original);
    StripUnusedGlobals.strip(tu);
    CompareAsts.assertEqualAsts(original, tu);
  }

}