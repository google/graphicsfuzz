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

package com.graphicsfuzz.generator.semanticschanging;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

public class Expr2ArgMutationFinderTest {


  @Test
  public void testExpr2ArgMiner() throws Exception {
    final String program = "#version 100\n"
          + "void main() {"
          + "  int a, b, c, d, e;"
          + "  a + (b + c) * (d - e);"
          + "}";
    final String expected = "#version 100\n"
          + "void main() {"
          + "  int a, b, c, d, e;"
          + "  a + (b) * (d - e);"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<Expr2ExprMutation> ops = new Expr2ArgMutationFinder(tu)
          .findMutations();
    assertEquals(10, ops.size());
    ops.get(0).apply();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
