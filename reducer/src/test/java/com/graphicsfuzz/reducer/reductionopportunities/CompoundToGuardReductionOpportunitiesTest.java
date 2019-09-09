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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompoundToGuardReductionOpportunitiesTest {

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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
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
        + "  i < 100;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testSwitchWithReduceEverywhere() throws Exception {
    // Fine to reduce this switch to its condition as we are not preserving semantics.
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoForWithReduceEverywhere() throws Exception {
    // Even though we are not preserving semantics, there is no reduction opportunity here due to
    // the declaration of i in the loop construct (using 'i < 100' on its own would be no good).
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  for (int i = 0; i < 100; i++) {\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  1 > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
    assertEquals(0, opportunities.size());
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  i < 100;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "      x > 0;\n"
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
        + "      z > 0;\n"
        + "    }\n"
        + "  }"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "    i < 100;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  " + Constants.LIVE_PREFIX + "x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
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
        + "  x > 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testSwitchWithPreserveSemanticsDeadCode() throws Exception {
    // Fine to reduce this switch to its condition despite preserving semantics as we are in dead
    // code.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  if (" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false))) {"
        + "    int x = 2;\n"
        + "    switch (x + 3) {\n"
        + "      default:\n"
        + "        x++;\n"
        + "        break;\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  if (" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false))) {"
        + "    int x = 2;\n"
        + "    x + 3;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, false);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testOpportunitiesDisablingEachOther() throws Exception {
    // Fine to reduce this switch to its condition despite preserving semantics as we are in dead
    // code.
    final String original = "#version 310 es\n"
        + "void main() {\n"
        + "  if (1 > 0) {\n"
        + "    while (1 > 0) {\n"
        + "    }\n"
        + "  }\n"
        + "  if (2 > 0) {\n"
        + "    do {\n"
        + "    } while(false);\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<CompoundToGuardReductionOpportunity> opportunities = getOpportunities(tu, true);
    assertEquals(4, opportunities.size());
    assertTrue(opportunities.get(1).preconditionHolds()); // This is the "if (1 > 0)" case
    opportunities.get(1).applyReductionImpl();

    final String expected_1 = "#version 310 es\n"
        + "void main() {\n"
        + "  1 > 0;\n"
        + "  if (2 > 0) {\n"
        + "    do {\n"
        + "    } while(false);\n"
        + "  }\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected_1, tu);

    assertTrue(opportunities.get(0).preconditionHolds()); // The "while (1 > 0)" case still
    // exists and can still be applied, but has no effect as that AST subtree is unreachable.
    opportunities.get(0).applyReductionImpl();
    CompareAsts.assertEqualAsts(expected_1, tu);

    assertTrue(opportunities.get(2).preconditionHolds());
    opportunities.get(2).applyReductionImpl();
    final String expected_2 = "#version 310 es\n"
        + "void main() {\n"
        + "  1 > 0;\n"
        + "  if (2 > 0) {\n"
        + "    false;\n"
        + "  }\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected_2, tu);

    assertTrue(opportunities.get(3).preconditionHolds());
    opportunities.get(3).applyReductionImpl();
    final String expected_3 = "#version 310 es\n"
        + "void main() {\n"
        + "  1 > 0;\n"
        + "  2 > 0;\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected_3, tu);

  }


  private List<CompoundToGuardReductionOpportunity> getOpportunities(TranslationUnit tu,
                                                                     boolean reduceEverywhere) {
    return CompoundToGuardReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(reduceEverywhere,
            ShadingLanguageVersion.ESSL_310, new RandomWrapper(0),
            new IdGenerator()));
  }

}
