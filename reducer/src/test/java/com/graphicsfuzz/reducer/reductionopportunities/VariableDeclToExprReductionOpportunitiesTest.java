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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VariableDeclToExprReductionOpportunitiesTest {

  // TODO(480): Enable this test once the issue is fixed.
  @Ignore
  @Test
  public void testDoNotReplace() throws Exception {
    final String original = "void main() { int a = 1, b = 2; int c = 3; }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertTrue(ops.isEmpty());
  }

  @Ignore
  @Test
  public void testMultipleDeclarations() throws Exception {
    final String program = "void main() {"
        + "int a = 1;"
        + "int b = foo();"
        + "int c;"
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
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(2, ops.size());
    for (int i = 0; i < ops.size(); i++) {
      ops.get(i).applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Ignore
  @Test
  public void testMultipleLineDeclarationsOneLine() throws Exception {
    final String program = "void main() {"
        + "int a;"
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
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(3, ops.size());
    for (int i = 0; i < ops.size(); i++) {
      ops.get(i).applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Ignore
  @Test
  public void testAssignVariableIdentifier() throws Exception {
    final String program = "void main() {"
        + "int a = 1;"
        + "int b = a, c = b;"
        + "int d = c;"
        + "}";
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
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(4, ops.size());
    for (int i = 0; i < ops.size(); i++) {
      ops.get(i).applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Ignore
  @Test
  public void testReplaceGlobalDeclaration() throws Exception {
    final String program = "int a = 1, b = 2;"
        + "void main() {"
        + "}";
    final String expected = "int a, b;"
        + "a = 1;"
        + "b = 2;"
        + "void main() {"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<VariableDeclToExprReductionOpportunity> ops = VariableDeclToExprReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0), null, true));
    assertEquals(2, ops.size());
    for (int i = 0; i < ops.size(); i++) {
      ops.get(i).applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
