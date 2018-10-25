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
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RemoveUnusedParameterReductionOpportunitiesTest {

  @Test
  public void testRemoveParam() throws Exception {
    final String shader = "float foo(int a, float b, float c) {" +
      "  return b;" +
      "}" +
      "" +
      "void main() {" +
      "  foo(5, 4.0, 2.0);" +
      "}";
    final String expectedResult = "float foo(float b) {" +
      "  return b;" +
      "}" +
      "" +
      "void main() {" +
      "  foo(4.0);" +
      "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    List<RemoveUnusedParameterReductionOpportunity> opportunities =
      findOpportunities(tu, true);
    assertEquals(2, opportunities.size());
    opportunities.get(0).applyReduction();
    opportunities =
      findOpportunities(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expectedResult, tu);
  }

  @Test
  public void testRemoveParam2() throws Exception {
    final String shader = "float foo(int a);" +
      "float foo(int a) {" +
      "  return 0.0;" +
      "}" +
      "void main() {" +
      "  foo(2.0);" +
      "}";
    final String expectedResult = "float foo();" +
      "float foo() {" +
      "  return 0.0;" +
      "}" +
      "void main() {" +
      "  foo();" +
      "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    List<RemoveUnusedParameterReductionOpportunity> opportunities =
      findOpportunities(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expectedResult, tu);
  }

  @Test
  public void testDoNotRemoveParamIfOverloaded() throws Exception {
    final String shader = "float foo(int a, float b) {" +
      "  return float(a);" +
      "}" +
      "void foo(int a) {" +
      "}" +
      "void main() {" +
      "  foo(2, 3.0);" +
      "  foo(2);" +
      "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    List<RemoveUnusedParameterReductionOpportunity> opportunities =
      findOpportunities(tu, true);
    assertEquals(0, opportunities.size());
  }

  @Test
  public void testRemoveParamOnlyIfReduceEverywhere() throws Exception {
    final String shader = "" +
      "float x = 0.0;" +
      "void bar(float a) { }" +
      "float foo() {" +
      "  x = 1.0;" +
      "  return 3.0;" +
      "}" +
      "void main() {" +
      "  bar(foo());" +
      "  gl_FragColor = vec4(x);" +
      "}";
    final String shaderIfReduced = "" +
      "float x = 0.0;" +
      "void bar() { }" +
      "float foo() {" +
      "  x = 1.0;" +
      "  return 3.0;" +
      "}" +
      "void main() {" +
      "  bar();" +
      "  gl_FragColor = vec4(x);" +
      "}";

    TranslationUnit tu = ParseHelper.parse(shader);
    List<RemoveUnusedParameterReductionOpportunity> opportunities =
      findOpportunities(tu, true);
    assertEquals(1, opportunities.size());
    opportunities.get(0).applyReduction();
    CompareAsts.assertEqualAsts(shaderIfReduced, tu);

    tu = ParseHelper.parse(shader);
    opportunities =
      findOpportunities(tu, false);
    assertEquals(0, opportunities.size());
  }

  private List<RemoveUnusedParameterReductionOpportunity> findOpportunities(TranslationUnit tu,
                                                                            boolean reduceEverywhere) {
    return RemoveUnusedParameterReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
      new ReducerContext(reduceEverywhere,
        ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null, true));
  }

}
