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
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

public class SwapVariableIdentifiersMutationFinderTest {

  @Test
  public void testSwapVariableIdentifiers() throws Exception {
    final String program = "#version 300 es\n"
        + "int foo(int x) {"
        + "  int s = 5;"
        + "  s += x * x + s * 3;"
        + "}"
        + "void main () {"
        + "  float a;"
        + "  int b;"
        + "  int c;"
        + "  for(int i = 0; i < 10; i++) {"
        + "    a = b + c;"
        + "  }"
        + "}";

    final String expected = "#version 300 es\n"
        + "int foo(int x) {"
        + "  int s = 5;"
        + "  x += s * s + x * 3;"
        + "}"
        + "void main () {"
        + "  float a;"
        + "  int b;"
        + "  int c;"
        + "  for(int i = 0; i < 10; i++) {"
        + "    a = i + b;"
        + "  }"
        + "}";

    TranslationUnit tu = ParseHelper.parse(program);

    final List<Expr2ExprMutation> ops = new SwapVariableIdentifiersMutationFinder(tu,
        new RandomWrapper(0)).findMutations();

    assertEquals(6, ops.size());

    ops.get(0).apply();
    ops.get(1).apply();
    ops.get(2).apply();
    ops.get(3).apply();
    ops.get(4).apply();
    ops.get(5).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }

}
