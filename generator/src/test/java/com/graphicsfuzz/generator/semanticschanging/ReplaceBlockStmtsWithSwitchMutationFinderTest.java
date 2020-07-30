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

public class ReplaceBlockStmtsWithSwitchMutationFinderTest {

  @Test
  public void testReplaceBlockStmtsWithSwitchMiner() throws Exception {

    final String program = "#version 300 es\n"
        + "int foo(int x) {"
        + "int a = 5;"
        + "int b = 7;"
        + "switch(x) {"
        + "   case 84:"
        + "   case 43:"
        + "   default:"
        + "   {"
        + "     switch(0) {"
        + "     case 0:"
        + "       return x;"
        + "     }"
        + "    }"
        + "  }"
        + "}"
        + "void main () {"
        + "  int a = 0;"
        + "  int b = 0;"
        + "  int c = 0;"
        + "  for(int i = 0; i < 10; i++) {"
        + "    a++;"
        + "    b += 2;"
        + "    c += 3;"
        + "  }"
        + "}";

    final String expected = "#version 300 es\n"
        + "int foo(int x)\n"
        + "{\n"
        + " switch(x)\n"
        + "  {\n"
        + "   case 17:\n"
        + "   case 82:\n"
        + "   case 7:\n"
        + "   case 28:\n"
        + "   case 4:\n"
        + "   case 35:\n"
        + "   case 11:\n"
        + "   case 15:\n"
        + "   case 22:\n"
        + "   case 47:\n"
        + "   case 96:\n"
        + "   int a = 5;\n"
        + "   case 81:\n"
        + "   case 42:\n"
        + "   case 76:\n"
        + "   case 48:\n"
        + "   case 64:\n"
        + "   case 91:\n"
        + "   int b = 7;\n"
        + "   case 83:\n"
        + "   case 55:\n"
        + "   case 89:\n"
        + "   case 14:\n"
        + "   case 79:\n"
        + "   case 54:\n"
        + "   default:\n"
        + "   {\n"
        + "    switch(x)\n"
        + "     {\n"
        + "      case 84:\n"
        + "      case 43:\n"
        + "      default:\n"
        + "      {\n"
        + "       switch(0)\n"
        + "        {\n"
        + "         case 0:\n"
        + "         return x;\n"
        + "        }\n"
        + "      }\n"
        + "     }\n"
        + "   }\n"
        + "  }\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " int a = 0;\n"
        + " int b = 0;\n"
        + " int c = 0;\n"
        + " for(\n"
        + "     int i = 0;\n"
        + "     i < 10;\n"
        + "     i ++\n"
        + " )\n"
        + "  {\n"
        + "   a ++;\n"
        + "   b += 2;\n"
        + "   c += 3;\n"
        + "  }\n"
        + "}";


    TranslationUnit tu = ParseHelper.parse(program);

    final List<ReplaceBlockStmtsMutation> ops = new ReplaceBlockStmtsWithSwitchMutationFinder(tu,
        new RandomWrapper(0))
        .findMutations();

    assertEquals(4, ops.size());

    ops.get(0).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testReplaceBlockStmtsWithSwitchMiner2() throws Exception {
    final String program = "#version 300 es\n"
        + "int foo(int x) {"
        + "}"
        + "void main() {"
        + "}";

    TranslationUnit tu = ParseHelper.parse(program);

    final List<ReplaceBlockStmtsMutation> ops = new ReplaceBlockStmtsWithSwitchMutationFinder(tu,
        new RandomWrapper(0))
        .findMutations();

    assertEquals(0, ops.size());
  }

  @Test
  public void testReplaceBlockStmtsWithSwitchMiner3() throws Exception {
    final String program = "#version 300 es\n"
        + "int foo(int x) {"
        + "  switch(x) {"
        + "    case 1:"
        + "    case 2:"
        + "    default:"
        + "       return 5;"
        + "  }"
        + "}"
        + "void main() {"
        + "}";

    final String expected = "#version 300 es\n"
        + "int foo(int x)\n"
        + "{\n"
        + " switch(x)\n"
        + "  {\n"
        + "   case 17:\n"
        + "   case 82:\n"
        + "   case 7:\n"
        + "   case 28:\n"
        + "   case 4:\n"
        + "   case 35:\n"
        + "   case 11:\n"
        + "   case 15:\n"
        + "   case 22:\n"
        + "   case 47:\n"
        + "   case 96:\n"
        + "   case 81:\n"
        + "   case 42:\n"
        + "   case 76:\n"
        + "   case 48:\n"
        + "   case 64:\n"
        + "   case 91:\n"
        + "   case 83:\n"
        + "   case 55:\n"
        + "   case 89:\n"
        + "   case 14:\n"
        + "   case 79:\n"
        + "   case 54:\n"
        + "   default:\n"
        + "   {\n"
        + "    switch(x)\n"
        + "     {\n"
        + "      case 1:\n"
        + "      case 2:\n"
        + "      default:\n"
        + "      return 5;\n"
        + "     }\n"
        + "   }\n"
        + "  }\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + "}\n";


    TranslationUnit tu = ParseHelper.parse(program);

    final List<ReplaceBlockStmtsMutation> ops = new ReplaceBlockStmtsWithSwitchMutationFinder(tu,
        new RandomWrapper(0))
        .findMutations();

    assertEquals(1, ops.size());

    ops.get(0).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }
}
