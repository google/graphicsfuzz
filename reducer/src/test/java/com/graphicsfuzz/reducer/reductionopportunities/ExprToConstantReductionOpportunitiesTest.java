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
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExprToConstantReductionOpportunitiesTest {

  @Test
  public void testOut() throws Exception {
    final String prog = "void f(out int x) { } void main() { int a; f(a); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), null, true));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(prog)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testInOut() throws Exception {
    final String prog = "void f(inout int x) { } void main() { int a; f(a); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), null, true));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(prog)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testIn() throws Exception {
    final String prog = "void f(in int x) { } void main() { int a; f(a); }";
    final String expectedProg = "void f(in int x) { } void main() { int a; f(1); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), null, true));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)), PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testSingleLiveVariable() throws Exception {
    final String program = "void main() { int GLF_live3_a; GLF_live3_a; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, ShadingLanguageVersion.ESSL_100, null, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts("void main() { int GLF_live3_a; 1; }", tu);
  }

}