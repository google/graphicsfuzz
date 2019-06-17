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
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlobalVariableDeclToExprReductionOpportunitiesTest {

  // TODO(519): Enable this test when the issue is solved.
  @Ignore
  @Test
  public void testDoNotReplace() throws Exception {
    final String original = "int a = 1; void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops =
        VariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null, true));
    // There should be no opportunities as the preserve semantics is enabled.
    assertTrue(ops.isEmpty());
  }

  @Ignore
  @Test
  public void testInsertAsFirstStatement() throws Exception {
    final String program = ""
        + "int a = 1;"
        + "void main() {"
        + " int b = a; "
        + "}";
    // We have to ensure that the new assignment statements is inserted
    // as the first statement in main() function.
    final String expected = ""
        + "int a;"
        + "void main() {"
        + " a = 1;"
        + " int b = a;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(1, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Ignore
  @Test
  public void testMultipleDeclarations() throws Exception {
    final String program = ""
        + "int a = 1;"      // Initialized variable declaration.
        + "int b = foo();"  // Initialized variable declaration.
        + "int c;"          // Uninitialized variable declaration.
        + "void main() { }";
    final String expected = ""
        + "int a;"
        + "int b;"
        + "int c;"          // Uninitialized variable declaration.
        + "void main() {"
        + " a = 1;"
        + " b = foo();"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    // Only variable declarations a and b have the initializer.
    // Thus, we expect the reducer to find only 2 opportunities.
    assertEquals(2, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Ignore
  @Test
  public void testDoNotReplaceConst() throws Exception {
    final String original = "const int a = 1; void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops =
        VariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), null, true));
    // There should be no opportunities as it is invalid to declare constant variable
    // without an initial value.
    assertTrue(ops.isEmpty());
  }

  @Ignore
  @Test
  public void testMultipleLineDeclarationsOneLine() throws Exception {
    final String program = ""
        + "int b = 1, c, d = foo(), e, f = bar();"  // This variable declaration has many
                                                    // declaration infos but we consider only
                                                    // the one that has initializer (b, d, and f).
        + "void main() { }";
    final String expected = ""
        + "int b, c, d, e, f;"
        + "void main() {"
        + " b = 1;"
        + " d = foo();"
        + " f = bar();"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(3, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testAssignVariableIdentifier() throws Exception {
    final String program = ""
        + "int a = 1;"
        + "int b = a, c = b;"   // b depends on a and c depends on b.
        + "int d = c;"          // d depends on c.
        + "void main() { }";
    // As here we have the variable identifier as the initializer, we need to
    // make sure that new expressions generated by the reducer are added
    // in the correct order.
    final String expected = ""
        + "int a;"
        + "int b, c;"
        + "int d;"
        + "void main() {"
        + " a = 1;"
        + " b = a;"
        + " c = b;"
        + " d = c;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(4, ops.size());
    ops.forEach(VariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }
}
