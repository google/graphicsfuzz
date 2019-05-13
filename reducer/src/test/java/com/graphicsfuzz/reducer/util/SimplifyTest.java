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
import org.junit.Ignore;
import org.junit.Test;

public class SimplifyTest {

  // TODO(110) - this test fails due to issue 110, and should be enabled once that issue is fixed.
  @Ignore
  @Test
  public void testParenthesesRemoved() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {"
        + "  if (_GLF_DEAD(_GLF_FALSE(false, false))) {"
        + "    _GLF_FUZZED(1);"
        + "  }"
        + "}");
    final String expected = "void main() {"
        + "  if(false) {"
        + "    1;"
        + "  }"
        + "}";
    final TranslationUnit simplifiedTu = Simplify.simplify(tu);
    CompareAsts.assertEqualAsts(expected, simplifiedTu);
  }

}
