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

package com.graphicsfuzz.reducer.reductionopportunities;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.Constants;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class FlattenControlFlowReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Test
  public void testUnderUnreachableSwitch() throws Exception {
    final String original = "void main() {"
          + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
          + "    case 1:"
          + "    if (true) {"
          + "      true;"
          + "    }"
          + "    case 2:"
          + "    if (true) {"
          + "      true;"
          + "    } else false;"
          + "    case 0:"
          + "    for (int i = 0; i < 100; i++) { }"
          + "    break;"
          + "    default:"
          + "    while(true) { 1; }"
          + "  }"
          + "}";
    final String expected1 = "void main() {"
          + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
          + "    case 1:"
          + "    { true; true; }"
          + "    case 2:"
          + "    if (true) {"
          + "      true;"
          + "    } else false;"
          + "    case 0:"
          + "    for (int i = 0; i < 100; i++) { }"
          + "    break;"
          + "    default:"
          + "    while(true) { 1; }"
          + "  }"
          + "}";
    final String expected2 = "void main() {"
          + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
          + "    case 1:"
          + "    if (true) {"
          + "      true;"
          + "    }"
          + "    case 2:"
          + "    { true; true; }"
          + "    case 0:"
          + "    for (int i = 0; i < 100; i++) { }"
          + "    break;"
          + "    default:"
          + "    while(true) { 1; }"
          + "  }"
          + "}";
    final String expected3 = "void main() {"
          + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
          + "    case 1:"
          + "    if (true) {"
          + "      true;"
          + "    }"
          + "    case 2:"
          + "    { true; false; }"
          + "    case 0:"
          + "    for (int i = 0; i < 100; i++) { }"
          + "    break;"
          + "    default:"
          + "    while(true) { 1; }"
          + "  }"
          + "}";
    final String expected4 = "void main() {"
          + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
          + "    case 1:"
          + "    if (true) {"
          + "      true;"
          + "    }"
          + "    case 2:"
          + "    if (true) {"
          + "      true;"
          + "    } else false;"
          + "    case 0:"
          + "    for (int i = 0; i < 100; i++) { }"
          + "    break;"
          + "    default:"
          + "    { true; 1; }"
          + "  }"
          + "}";
    check(false, original, expected1, expected2, expected3, expected4);
  }

  @Test
  public void testDoNotRemoveDeadIf() throws Exception {
    final String original = ""
          + "void main() {"
          + "  int a = 2;"
          + "  if (" + Constants.GLF_DEAD + "(false)) {"
          + "    a = a + 1;"
          + "  }"
          + "}";
    final List<AbstractReductionOpportunity> opportunities =
        getOps(ParseHelper.parse(original), false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testInDeadCode() throws Exception {
    final String original = ""
          + "void main() {"
          + "  if (" + Constants.GLF_DEAD + "(false)) {"
          + "    if (false) {"
          + "      int a = 2;"
          + "      a = a + 1;"
          + "    }"
          + "  }"
          + "}";
    final String expected = ""
          + "void main() {"
          + "  if (" + Constants.GLF_DEAD + "(false)) {"
          + "    {"
          + "      false;"
          + "      int a = 2;"
          + "      a = a + 1;"
          + "    }"
          + "  }"
          + "}";
    check(false, original, expected);
  }

  @Test
  public void testDeadIfReduceEverywhere() throws Exception {
    final String original = ""
        + "void main() {"
        + "  if (" + Constants.GLF_DEAD + "(false)) {"
        + "    if (" + Constants.GLF_DEAD + "(false)) {"
        + "      int a = 2;"
        + "      a = a + 1;"
        + "    }"
        + "  }"
        + "}";
    final String expected1 = ""
        + "void main() {"
        + "  {" // first if changed to block
        + "    " + Constants.GLF_DEAD + "(false);"
        + "    if (" + Constants.GLF_DEAD + "(false)) {"
        + "      int a = 2;"
        + "      a = a + 1;"
        + "    }"
        + "  }"
        + "}";
    final String expected2 = ""
        + "void main() {"
        + "  if (" + Constants.GLF_DEAD + "(false)) {"
        + "    {" // second if changed to block
        + "      " + Constants.GLF_DEAD + "(false);"
        + "      int a = 2;"
        + "      a = a + 1;"
        + "    }"
        + "  }"
        + "}";
    check(true, original, expected1, expected2);
  }

  @Test
  public void testInDeadCode2() throws Exception {
    final String original = ""
          + "void main() {"
          + "  int a = 4;"
          + "  if (" + Constants.GLF_DEAD + "(false)) {"
          + "    if (a) {"
          + "      if (a > 0) {"
          + "        int a = 2;"
          + "        a = a + 1;"
          + "      } else"
          + "        a++;"
          + "    }"
          + "  }"
          + "}";
    final String expected1 = ""
        + "void main() {"
        + "  int a = 4;"
        + "  if (" + Constants.GLF_DEAD + "(false)) {"
        + "    {"
        + "      a;"
        + "      if (a > 0) {"
        + "        int a = 2;"
        + "        a = a + 1;"
        + "      } else"
        + "        a++;"
        + "    }"
        + "  }"
        + "}";
    final String expected2 = ""
        + "void main() {"
        + "  int a = 4;"
        + "  if (" + Constants.GLF_DEAD + "(false)) {"
        + "    if (a) {"
        + "      {"
        + "        a > 0;"
        + "        int a = 2;"
        + "        a = a + 1;"
        + "      }"
        + "    }"
        + "  }"
        + "}";
    final String expected3 = ""
        + "void main() {"
        + "  int a = 4;"
        + "  if (" + Constants.GLF_DEAD + "(false)) {"
        + "    if (a) {"
        + "      {"
        + "        a > 0;"
        + "        a++;"
        + "      }"
        + "    }"
        + "  }"
        + "}";
    check(false, original, expected1, expected2, expected3);
  }

  @Test
  public void testInLiveCode() throws Exception {
    final String original = "void main() {"
          + "  int x;"
          + "  {"
          + "    int GLF_live1hello = 4;"
          + "    if (GLF_live1hello < 46)"
          + "      GLF_live1hello++;"
          + "  }"
          + "  if (true) {"
          + "    x++;"
          + "  }"
          + "}";
    final String expected = "void main() {"
          + "  int x;"
          + "  {"
          + "    int GLF_live1hello = 4;"
          + "    {"
          + "      GLF_live1hello < 46;"
          + "      GLF_live1hello++;"
          + "    }"
          + "  }"
          + "  if (true) {"
          + "    x++;"
          + "  }"
          + "}";
    check(false, original, expected);
  }

  @Test
  public void testInDeadFunction() throws Exception {
    final String original = ""
          + "int foo(int x) {"
          + "  do {"
          + "    x++;"
          + "  } while(x < 100);"
          + "}"
          + "int bar(int x) {"
          + "  do {"
          + "    x++;"
          + "    break;"
          + "  } while(x < 100);"
          + "}"
          + "int baz(int x) {"
          + "  do "
          + "    break;"
          + "  while(x < 100);"
          + "}"
          + "void main() {"
          + "}";
    final String expected = ""
          + "int foo(int x) {"
          + "  {"
          + "    x++;"
          + "    x < 100;"
          + "  }"
          + "}"
          + "int bar(int x) {"
          + "  do {"
          + "    x++;"
          + "    break;"
          + "  } while(x < 100);"
          + "}"
          + "int baz(int x) {"
          + "  do "
          + "    break;"
          + "  while(x < 100);"
          + "}"
          + "void main() {"
          + "}";
    check(false, original, expected);
  }

  @Test
  public void testReduceEverywhere() throws Exception {
    final String original = ""
          + "void main() {"
          + "  for(int i = 0; i < 100; i++) {"
          + "    for (int j = 0; j < 200; j++) {"
          + "      if (i > j) {"
          + "      } else if(j > 5) {"
          + "         while(j > 5) j--;"
          + "      }"
          + "      for (int t = 1; t < 4; t++) {"
          + "        continue;"
          + "      }"
          + "    }"
          + "  }"
          + "}";

    final String expected1 = ""
        + "void main() {"
        + "  {"
        + "    int i = 0;"
        + "    i < 100;"
        + "    for (int j = 0; j < 200; j++) {"
        + "      if (i > j) {"
        + "      } else if(j > 5) {"
        + "         while(j > 5) j--;"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "    }"
        + "    i++;"
        + "  }"
        + "}";

    final String expected2 = ""
        + "void main() {"
        + "  for(int i = 0; i < 100; i++) {"
        + "    {"
        + "      int j = 0;"
        + "      j < 200;"
        + "      if (i > j) {"
        + "      } else if(j > 5) {"
        + "         while(j > 5) j--;"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "      j++;"
        + "    }"
        + "  }"
        + "}";

    final String expected3 = ""
        + "void main() {"
        + "  for(int i = 0; i < 100; i++) {"
        + "    for (int j = 0; j < 200; j++) {"
        + "      {"
        + "        i > j;"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "    }"
        + "  }"
        + "}";

    final String expected4 = ""
        + "void main() {"
        + "  for(int i = 0; i < 100; i++) {"
        + "    for (int j = 0; j < 200; j++) {"
        + "      {"
        + "        i > j;"
        + "        if(j > 5) {"
        + "          while(j > 5) j--;"
        + "        }"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "    }"
        + "  }"
        + "}";

    final String expected5 = ""
        + "void main() {"
        + "  for(int i = 0; i < 100; i++) {"
        + "    for (int j = 0; j < 200; j++) {"
        + "      if (i > j) {"
        + "      } else {"
        + "         j > 5;"
        + "         while(j > 5) j--;"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "    }"
        + "  }"
        + "}";

    final String expected6 = ""
        + "void main() {"
        + "  for(int i = 0; i < 100; i++) {"
        + "    for (int j = 0; j < 200; j++) {"
        + "      if (i > j) {"
        + "      } else if(j > 5) {"
        + "        {"
        + "          j > 5;"
        + "          j--;"
        + "        }"
        + "      }"
        + "      for (int t = 1; t < 4; t++) {"
        + "        continue;"
        + "      }"
        + "    }"
        + "  }"
        + "}";

    check(true, original, expected1, expected2, expected3, expected4, expected5, expected6);
  }

  @Test
  public void reduceEverywhereSimpleFor() throws Exception {
    final String original = "void main() {"
          + "  for (int i = 0; i < 10; i++) ;"
          + "}";
    final String expected = "void main() {"
          + "  { int i = 0; i < 10; ; i++; }"
          + "}";
    check(true, original, expected);
  }

  @Test
  public void reduceEverywhereSimpleFor2() throws Exception {
    final String original = "void main() {"
          + "  for (int i = 0; i < 10; i++) { }"
          + "}";
    final String expected = "void main() {"
          + "  { int i = 0; i < 10; i++; }"
          + "}";
    check(true, original, expected);
  }

  private void check(boolean reduceEverywhere, String original, String... expected)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final TranslationUnit tu = ParseHelper.parse(original);
    List<AbstractReductionOpportunity> ops =
          getOps(tu, reduceEverywhere);
    final Set<String> actualSet = new HashSet<>();
    for (int i = 0; i < ops.size(); i++) {
      final TranslationUnit clonedTu = tu.clone();
      List<AbstractReductionOpportunity> clonedOps =
            getOps(clonedTu, reduceEverywhere);
      assertEquals(ops.size(), clonedOps.size());
      clonedOps.get(i).applyReduction();
      actualSet.add(PrettyPrinterVisitor.prettyPrintAsString(clonedTu));
    }
    final Set<String> expectedSet = new HashSet<>();
    for (String anExpected : expected) {
      expectedSet.add(PrettyPrinterVisitor
          .prettyPrintAsString(ParseHelper.parse(anExpected)));
    }
    assertEquals(expectedSet, actualSet);
  }

  @Test
  public void testDoNotTurnForLoopIntoBlock() throws Exception {
    final String shader = "#version 310 es\n"
        + "void GLF_live4doConvert()\n"
        + "{\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " vec4 c = vec4(0.0, 0.0, 0.0, 1.0);\n"
        + " for(\n"
        + "     int i = 0;\n"
        + "     i < 3;\n"
        + "     i ++\n"
        + " )\n"
        + "  {\n"
        + "     c[i] = c[i] * c[i];\n"
        + "  }\n"
        + " " + Constants.LIVE_PREFIX + "4doConvert();\n"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final List<AbstractReductionOpportunity> ops =
        getOps(tu, false);
    assertEquals(0, ops.size());
  }

  @Test
  public void doNotReplaceDeadCodeInjectionWithBody() throws Exception {
    final String original = "void main() {"
        + "  if (" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false))) {"
        + "    discard;"
        + "  }"
        + "}";
    final List<AbstractReductionOpportunity> opportunities =
        getOps(ParseHelper.parse(original), false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testIfWithReduceEverywhere() throws Exception {
    // Fine to reduce this conditional to its guard as we are not preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  if (x > 0) {\n"
        + "    ++x;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  {\n"
        + "    x > 0;\n"
        + "    ++x;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testForWithReduceEverywhere() throws Exception {
    // Fine to reduce this loop to its guard as we are not preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for (i = 0; i < 100; i++) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  {\n"
        + "    i = 0;\n"
        + "    i < 100;\n"
        + "    i++;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testWhileWithReduceEverywhere() throws Exception {
    // Fine to reduce this loop to its guard as we are not preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  while (x > 0) {\n"
        + "    x--;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  {\n"
        + "    x > 0;"
        + "    x--;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoWhileWithReduceEverywhere() throws Exception {
    // Fine to reduce this loop to its guard as we are not preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  do {\n"
        + "    x++;\n"
        + "  } while (x > 0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  {\n"
        + "    x++;\n"
        + "    x > 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoIfWithPreserveSemantics() throws Exception {
    // No reduction opportunities here if preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  if (x > 0) {\n"
        + "    ++x;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testNoForWithPreserveSemantics() throws Exception {
    // No reduction opportunities here if preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  for (i = 0; i < 100; i++) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  i < 100;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testNoWhileWithPreserveSemantics() throws Exception {
    // No reduction opportunities here if preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  while (x > 0) {\n"
        + "    x--;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testNoDoWhileWithPreserveSemantics() throws Exception {
    // No reduction opportunities here if preserving semantics.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  do {\n"
        + "    x++;\n"
        + "  } while (x > 0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testNoSwitchWithPreserveSemantics() throws Exception {
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  switch (x + 3) {\n"
        + "    default:\n"
        + "      x++;\n"
        + "      break;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  x + 3;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testIfWithPreserveSemanticsSideEffectFree() throws Exception {
    // Despite preserving semantics, fine to reduce this conditional to its guard as the
    // conditional is side effect-free.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  if (x > 0) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  {\n"
        + "    x > 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testForWithPreserveSemanticsSideEffectFree() throws Exception {
    // Despite preserving semantics, fine to reduce this loop to its guard as the loop is side
    // effect-free.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  i = 0;\n"
        + "  for (; i < 100;) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  i = 0;\n"
        + "  {\n"
        + "    ;\n"
        + "    i < 100;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testWhileWithPreserveSemanticsSideEffectFree() throws Exception {
    // Despite preserving semantics, fine to reduce this loop to its guard as the loop is side
    // effect-free.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  while (x > 0) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  {\n"
        + "    x > 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoWhileWithPreserveSemanticsSideEffectFree() throws Exception {
    // Despite preserving semantics, fine to reduce this loop to its guard as the loop is side
    // effect-free.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  do {\n"
        + "  } while (x > 0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int x = 100;\n"
        + "  {\n"
        + "    x > 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testIfWithPreserveSemanticsUnreachableSwitch() throws Exception {
    // Fine to reduce two of these loops to their guards despite preserving semantics as they are
    // under unreachable switch cases.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
        + "    case 1:"
        + "    {\n"
        + "      int x = 2;\n"
        + "      if (x > 0) {\n"
        + "        ++x;\n"
        + "      }\n"
        + "    }\n"
        + "    case 0:"
        + "    {\n"
        + "      int y = 2;\n"
        + "      if (y > 0) {\n"
        + "        ++y;\n"
        + "      }\n"
        + "    }\n"
        + "    break;\n"
        + "    default:\n"
        + "    {\n"
        + "      int z = 2;\n"
        + "      if (z > 0) {\n"
        + "        ++z;\n"
        + "      }\n"
        + "    }\n"
        + "  }"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(" + Constants.GLF_SWITCH + "(0)) {"
        + "    case 1:"
        + "    {\n"
        + "      int x = 2;\n"
        + "      {\n"
        + "        x > 0;\n"
        + "        ++x;\n"
        + "      }\n"
        + "    }\n"
        + "    case 0:"
        + "    {\n"
        + "      int y = 2;\n"
        + "      if (y > 0) {\n"
        + "        ++y;\n"
        + "      }\n"
        + "    }\n"
        + "    break;\n"
        + "    default:\n"
        + "    {\n"
        + "      int z = 2;\n"
        + "      {\n"
        + "        z > 0;\n"
        + "        ++z;\n"
        + "      }\n"
        + "    }\n"
        + "  }"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(2, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    opportunities.get(1).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testForWithPreserveSemanticsDeadCode() throws Exception {
    // Fine to reduce this loop to its guard despite preserving semantics as we are in dead code.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  if (" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false))) {"
        + "    for (i = 0; i < 100; i++) {\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int i;\n"
        + "  if (" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false))) {"
        + "    {\n"
        + "      i = 0;\n"
        + "      i < 100;\n"
        + "      i++;\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testWhileWithPreserveSemanticsLiveCode() throws Exception {
    // Fine to reduce this loop to its guard despite preserving semantics as we are in injected
    // live code.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  int " + Constants.LIVE_PREFIX + "x = 100;\n"
        + "  while (" + Constants.LIVE_PREFIX + "x > 0) {\n"
        + "    " + Constants.LIVE_PREFIX + "x--;\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  int " + Constants.LIVE_PREFIX + "x = 100;\n"
        + "  {\n"
        + "    " + Constants.LIVE_PREFIX + "x > 0;\n"
        + "    " + Constants.LIVE_PREFIX + "x--;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoWhileWithPreserveSemanticsDeadFunction() throws Exception {
    // Fine to reduce this loop to its guard despite preserving semantics as we are in a dead
    // function.
    final String original = "#version 310 es\n"
        + "void " + Constants.GLF_DEAD + "_foo() {\n"
        + "  int x = 100;\n"
        + "  do {\n"
        + "    x++;\n"
        + "  } while (x > 0);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void " + Constants.GLF_DEAD + "_foo() {\n"
        + "  int x = 100;\n"
        + "  {\n"
        + "    x++;\n"
        + "    x > 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<AbstractReductionOpportunity> opportunities = getOps(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  private List<AbstractReductionOpportunity> getOps(TranslationUnit tu,
                                                    boolean reduceEverywhere) {
    return FlattenControlFlowReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(reduceEverywhere, true,
            tu.getShadingLanguageVersion(), new RandomWrapper(0),
            new IdGenerator()));
  }

}
