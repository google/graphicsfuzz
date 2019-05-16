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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompoundToBlockReductionOpportunitiesTest {

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
          + "    { true; }"
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
          + "    { true; }"
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
          + "    false;"
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
          + "    { 1; }"
          + "  }"
          + "}";
    check(false, original, expected1, expected2, expected3, expected4);
  }

  @Test
  public void testDoNotRemoveDeadIf() throws Exception {
    final String original = ""
          + "void main() {"
          + "  if (" + Constants.GLF_DEAD + "(false)) {"
          + "    int a = 2;"
          + "    a = a + 1;"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(original);
    assertTrue(CompoundToBlockReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
          ShadingLanguageVersion.GLSL_440, new RandomWrapper(0), null, true)).isEmpty());
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
          + "      int a = 2;"
          + "      a = a + 1;"
          + "    }"
          + "  }"
          + "}";
    check(false, original, expected);
  }

  // TODO(482): Enable this test once the issue is fixed.
  @Ignore
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
          + "      a++;"
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
          + "    GLF_live1hello++;"
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

    final String expected2 = ""
          + "void main() {"
          + "  for(int i = 0; i < 100; i++) {"
          + "    {"
          + "      int j = 0;"
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
    final String expected3 = ""
          + "void main() {"
          + "  for(int i = 0; i < 100; i++) {"
          + "    for (int j = 0; j < 200; j++) {"
          + "      {"
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
          + "      if(j > 5) {"
          + "         while(j > 5) j--;"
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
          + "      } else if(j > 5) {"
          + "         j--;"
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
          + "      } else {"
          + "         while(j > 5) j--;"
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
          + "  { int i = 0; ; }"
          + "}";
    check(true, original, expected);
  }

  @Test
  public void reduceEverywhereSimpleFor2() throws Exception {
    final String original = "void main() {"
          + "  for (int i = 0; i < 10; i++) { }"
          + "}";
    final String expected = "void main() {"
          + "  { int i = 0; }"
          + "}";
    check(true, original, expected);
  }

  private void check(boolean reduceEverywhere, String original, String... expected)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final TranslationUnit tu = ParseHelper.parse(original);
    List<CompoundToBlockReductionOpportunity> ops =
          getOps(tu, reduceEverywhere);
    final Set<String> actualSet = new HashSet<>();
    for (int i = 0; i < ops.size(); i++) {
      final TranslationUnit clonedTu = tu.clone();
      List<CompoundToBlockReductionOpportunity> clonedOps =
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

  private List<CompoundToBlockReductionOpportunity> getOps(TranslationUnit tu,
        boolean reduceEverywhere) {
    return ReductionOpportunities.
        getReductionOpportunities(
            MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(
                reduceEverywhere,
                ShadingLanguageVersion.GLSL_440,
                new RandomWrapper(0),
                new IdGenerator(),
                true),
            fileOps
        ).stream()
        .filter(item -> item instanceof CompoundToBlockReductionOpportunity)
        .map(item -> (CompoundToBlockReductionOpportunity) item)
        .collect(Collectors.toList());
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
    final List<CompoundToBlockReductionOpportunity> ops =
        CompoundToBlockReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, ShadingLanguageVersion.ESSL_310,
                new RandomWrapper(0), new IdGenerator(), true));
    assertEquals(0, ops.size());
  }

}
