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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

public class VariableDeclToExprReductionOpportunitiesTest {

  @Test
  public void testDoNotReplace() throws Exception {
    final String original = "void main() { int a = 1, b = 2; int c = 3; }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops =
        VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    // There should be no opportunities as the preserve semantics is enabled.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testDoNotReplaceConst() throws Exception {
    final String original = "void main() { const int a = 1;}";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops =
        VariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    // There should be no opportunities as it is invalid to declare constant variable
    // without an initial value.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testMultipleDeclarations() throws Exception {
    final String program = "void main() {"
        + "int a = 1;"      // Initialized variable declaration.
        + "int b = foo();"  // Initialized variable declaration.
        + "int c;"          // Uninitialized variable declaration.
        + "}";
    final String expected = "void main() {"
        + " int a;"
        + " a = 1;"
        + " int b;"
        + " b = foo();"
        + " int c;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    // Only variable declarations a and b have the initializer.
    // Thus, we expect the reducer to find only 2 opportunities.
    assertEquals(2, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testMultipleLineDeclarationsOneLine() throws Exception {
    final String program = "void main() {"
        + "int a;"
        // This variable declaration has many declaration infos but we consider only the one that
        // has initializer (b, d, and f).
        + "int b = 1, c, d = foo(), e, f = bar(); "
        + "int g;"
        + "}";
    final String expected = "void main() {"
        + " int a;"
        + " int b, c, d, e, f;"
        + " b = 1;"
        + " d = foo();"
        + " f = bar();"
        + " int g;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(3, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testAssignVariableIdentifier() throws Exception {
    final String program = "void main() {"
        + "int a = 1;"
        + "int b = a, c = b;"   // b depends on a and c depends on b.
        + "int d = c;"          // d depends on c.
        + "}";
    // As here we have the variable identifier as the initializer, we need to
    // make sure that new expressions generated by the reducer are added
    // in the correct order.
    final String expected = "void main() {"
        + " int a;"
        + " a = 1;"
        + " int b, c;"
        + " b = a;"
        + " c = b;"
        + " int d;"
        + " d = c;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(4, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
