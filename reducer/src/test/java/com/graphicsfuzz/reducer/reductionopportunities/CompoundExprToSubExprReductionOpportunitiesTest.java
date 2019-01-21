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
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompoundExprToSubExprReductionOpportunitiesTest {

  @Test
  public void noEffectInRegularCode() throws Exception {
    final String original = "void main() { int a = 2; int b = 3; a + b + b; }";
    final TranslationUnit tu = ParseHelper.parse(original);
    final List<SimplifyExprReductionOpportunity> ops = CompoundExprToSubExprReductionOpportunities
          .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.GLSL_440,
                new RandomWrapper(0), null, true));
    assertTrue(ops.isEmpty());
  }

  @Test
  public void someEffectInUnreachableFunction() throws Exception {
    final String original = "void foo() { int a = 2; int b = 3; int c = 4; a + b + c; }"
          + "void main() { }";
    final String expected1 = "void foo() { int a = 2; int b = 3; int c = 4; a + b; }"
          + "void main() { }";
    final String expected2 = "void foo() { int a = 2; int b = 3; int c = 4; c; }"
          + "void main() { }";
    final String expected3 = "void foo() { int a = 2; int b = 3; int c = 4; a + c; }"
          + "void main() { }";
    final String expected4 = "void foo() { int a = 2; int b = 3; int c = 4; b + c; }"
          + "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(original);
    check(false, original, expected1, expected2, expected3, expected4);
  }

  @Test
  public void functionCall() throws Exception {
    final String original = "int foo(int a, int b, float c);"
          + "void main() { foo(10, 20, 30.0); }";
    final String expected1 = "int foo(int a, int b, float c);"
          + "void main() { 10; }";
    final String expected2 = "int foo(int a, int b, float c);"
          + "void main() { 20; }";
    check(true, original, expected1, expected2);
  }

  @Test
  public void matrixScalar() throws Exception {
    final String original = "void foo(mat4 m) {"
          + "  m * 2.0;"
          + "}";
    final String expected1 = "void foo(mat4 m) {"
          + "  m;"
          + "}";
    check(true, original, expected1);
  }

  private void check(boolean reduceEverywhere, String original, String... expected)
      throws IOException, ParseTimeoutException, InterruptedException {
    final TranslationUnit tu = ParseHelper.parse(original);
    List<SimplifyExprReductionOpportunity> ops =
          getOps(tu, reduceEverywhere);
    final Set<String> actualSet = new HashSet<>();
    for (int i = 0; i < ops.size(); i++) {
      final TranslationUnit clonedTu = tu.clone();
      List<SimplifyExprReductionOpportunity> clonedOps =
            getOps(clonedTu, reduceEverywhere);
      assertEquals(ops.size(), clonedOps.size());
      clonedOps.get(i).applyReduction();
      actualSet.add(PrettyPrinterVisitor.prettyPrintAsString(clonedTu));
    }
    final Set<String> expectedSet = new HashSet<>();
    for (int i = 0; i < expected.length; i++) {
      expectedSet.add(PrettyPrinterVisitor
            .prettyPrintAsString(ParseHelper.parse(expected[i])));
    }
    assertEquals(expectedSet, actualSet);
  }

  private List<SimplifyExprReductionOpportunity> getOps(TranslationUnit tu,
        boolean reduceEverywhere) {
    return CompoundExprToSubExprReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(reduceEverywhere, ShadingLanguageVersion.GLSL_440,
          new RandomWrapper(0), null, true));
  }

}
