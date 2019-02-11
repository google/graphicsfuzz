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
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.io.IOException;
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
        + "int foo(int x)"
        + "{"
        + " switch(x)"
        + "  {"
        + "   case 84:"
        + "   case 43:"
        + "   case 44:"
        + "   int a = 5;"
        + "   case 62:"
        + "   case 77:"
        + "   int b = 7;"
        + "   case 75:"
        + "   case 20:"
        + "   case 41:"
        + "   case 73:"
        + "   case 95:"
        + "   default:"
        + "   {"
        + "    switch(x)"
        + "     {"
        + "      case 84:"
        + "      case 43:"
        + "      default:"
        + "      {"
        + "       switch(0)"
        + "        {"
        + "         case 0:"
        + "         return x;"
        + "        }"
        + "      }"
        + "     }"
        + "   }"
        + "  }"
        + "}"
        + "void main()"
        + "{"
        + " int a = 0;"
        + " int b = 0;"
        + " int c = 0;"
        + " for("
        + "     int i = 0;"
        + "     i < 10;"
        + "     i ++"
        + " )"
        + "  {"
        + "   a ++;"
        + "   b += 2;"
        + "   c += 3;"
        + "  }"
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
        + "int foo(int x) {"
        + "switch(x)"
        + "  {"
        + "   case 84:"
        + "   case 43:"
        + "   case 44:"
        + "   case 62:"
        + "   case 77:"
        + "   case 75:"
        + "   case 20:"
        + "   case 41:"
        + "   case 73:"
        + "   case 95:"
        + "   default:"
        + "   {"
        + "    switch(x)"
        + "     {"
        + "      case 1:"
        + "      case 2:"
        + "      default:"
        + "      return 5;"
        + "     }"
        + "   }"
        + "  }"
        + "}"
        + "void main() {"
        + "}";


    TranslationUnit tu = ParseHelper.parse(program);

    final List<ReplaceBlockStmtsMutation> ops = new ReplaceBlockStmtsWithSwitchMutationFinder(tu,
        new RandomWrapper(0))
        .findMutations();

    assertEquals(1, ops.size());

    ops.get(0).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }
}
