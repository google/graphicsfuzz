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

public class GlobalVariableDeclToExprReductionOpportunitiesTest {

  @Test
  public void testDoNotReplace() throws Exception {
    final String original = "int a = 1; void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    // There should be no opportunities as the preserve semantics is enabled.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testZeroMethod() throws Exception {
    final String original = "int a = 1;";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    // Since the new assignment statement must be only inserted as the first statement of
    // the main function, thus we have to ensure that main function exists.
    // In this case, there is no method at all.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testNoMainMethod() throws Exception {
    final String original = "int a = 1; void foo() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    // Since the new assignment statement must be only inserted as the first statement of
    // the main function, thus we have to ensure that main function exists.
    // In this case, there is one method but it is not main though so there should be no
    // opportunities.
    assertTrue(ops.isEmpty());
  }

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
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.forEach(GlobalVariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

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
        + " b = foo();"
        + " a = 1;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    // Only variable declarations a and b have the initializer.
    // Thus, we expect the reducer to find only 2 opportunities.
    assertEquals(2, ops.size());
    ops.forEach(GlobalVariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testDoNotReplaceConst() throws Exception {
    final String original = "const int a = 1; void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    // There should be no opportunities as it is invalid to declare constant variable
    // without an initial value.
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testMultipleLineDeclarationsOneLine() throws Exception {
    final String program = ""
        // This variable declaration has many declaration infos but we consider only the one that
        // has initializer (b, d, and f).
        + "int b = 1, c, d = foo(), e, f = bar();"
        + "void main() { }";
    final String expected = ""
        + "int b, c, d, e, f;"
        + "void main() {"
        + " f = bar();"
        + " d = foo();"
        + " b = 1;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(3, ops.size());
    ops.forEach(GlobalVariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testAssignVariableIdentifier() throws Exception {
    final String program = ""
        + "int a = 1;"
        + "int b = a, c = b;"   // b depends on a and c depends on b.
        + "int d = c;"          // d depends on c.
        + "void main() { }";
    // No need to retain the order of the assignment as we are running the semantics-changing mode.
    final String expected = ""
        + "int a;"
        + "int b, c;"
        + "int d;"
        + "void main() {"
        + " d = c;"
        + " c = b;"
        + " b = a;"
        + " a = 1;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(4, ops.size());
    ops.forEach(GlobalVariableDeclToExprReductionOpportunity::applyReductionImpl);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testGlobalVariableDeclAfterMain() throws Exception {
    // The global variable declarations that have found after main method will not be considered.
    final String original = "void main() { } int a = 1;";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<GlobalVariableDeclToExprReductionOpportunity> ops =
        GlobalVariableDeclToExprReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
                true, ShadingLanguageVersion.ESSL_100,
                new RandomWrapper(0), new IdGenerator()));
    assertTrue(ops.isEmpty());
  }
}
