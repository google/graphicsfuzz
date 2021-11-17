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
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.List;
import org.junit.Test;

public class ExprToConstantReductionOpportunitiesTest {

  @Test
  public void testOut() throws Exception {
    final String prog = "void f(out int x) { } void main() { int a; f(a); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), new IdGenerator()));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(prog)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testInOut() throws Exception {
    final String prog = "void f(inout int x) { } void main() { int a; f(a); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), new IdGenerator()));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(prog)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testIn() throws Exception {
    final String prog = "void f(in int x) { } void main() { int a; f(a); }";
    final String expectedProg = "void f(in int x) { } void main() { int a; f(1); }";
    TranslationUnit tu = ParseHelper.parse(prog);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(0), new IdGenerator()));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProg)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testSingleLiveVariable() throws Exception {
    final String program = "void main() { int GLF_live3_a; GLF_live3_a; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
              new RandomWrapper(0),
              new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts("void main() { int GLF_live3_a; 1; }", tu);
  }

  @Test
  public void testSwitch() throws Exception {
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(1) {\n"
        + "    case - 1:\n"
        + "      1;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testSwitchMultipleCases() throws Exception {
    // This test checks that switch cases are not simplified to canonical literals, because in the
    // following we do not want multiple identical cases:
    final String program = "#version 310 es\n"
        + "void main() {\n"
        + "  switch(1) {\n"
        + "    case 1:\n"
        + "    case 2:\n"
        + "    case 3:\n"
        + "      1;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, true, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testSimplifyLiterals() throws Exception {
    final String program = "#version 310 es\n"
        + "struct S {\n"
        + "  int a;\n"
        + "  float b;\n"
        + "};\n"
        + "void main() {\n"
        + "  100;\n"
        + "  200.0;\n"
        + "  300u;\n"
        + "  false;\n"
        + "  vec2(10.0, 1.0);\n"
        + "  vec3(11.0);\n"
        + "  vec4(2.0, 3.0, 4.0);\n"
        + "  ivec2(1, 2);\n"
        + "  ivec3(1, 4, 6);\n"
        + "  ivec4(1, 8, 9);\n"
        + "  uvec2(6u);\n"
        + "  uvec3(7u, 8u);\n"
        + "  uvec4(9u, 10u, 11u, 12u);\n"
        + "  bvec2(false, true);\n"
        + "  bvec3(true, false, true);\n"
        + "  bvec4(false, true, false, true);\n"
        + "  mat2x2(10.0, 20.0, 30.0, 40.0);\n"
        + "  mat2x3(10.0, 20.0, 30.0, 40.0, 50.0, 60.0);\n"
        + "  mat2x4(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0);\n"
        + "  mat3x2(10.0, 20.0, 30.0, 40.0, 50.0, 60.0);\n"
        + "  mat3x3(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0);\n"
        + "  mat3x4(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0);\n"
        + "  mat4x2(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0);\n"
        + "  mat4x3(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0);\n"
        + "  mat4x4(10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0, 110.0, 120.0, "
             + "130.0, 140.0, 150.0, 160.0);\n"
        + "  S(6, 2.0);\n"
        + "}";
    final String expected = "#version 310 es\n"
        + "struct S {\n"
        + "  int a;\n"
        + "  float b;\n"
        + "};\n"
        + "void main() {\n"
        + "  1;\n"
        + "  1.0;\n"
        + "  1u;\n"
        + "  true;\n"
        + "  vec2(1.0);\n"
        + "  vec3(1.0);\n"
        + "  vec4(1.0);\n"
        + "  ivec2(1);\n"
        + "  ivec3(1);\n"
        + "  ivec4(1);\n"
        + "  uvec2(1u);\n"
        + "  uvec3(1u);\n"
        + "  uvec4(1u);\n"
        + "  bvec2(true);\n"
        + "  bvec3(true);\n"
        + "  bvec4(true);\n"
        + "  mat2(1.0);\n"
        + "  mat2x3(1.0);\n"
        + "  mat2x4(1.0);\n"
        + "  mat3x2(1.0);\n"
        + "  mat3(1.0);\n"
        + "  mat3x4(1.0);\n"
        + "  mat4x2(1.0);\n"
        + "  mat4x3(1.0);\n"
        + "  mat4(1.0);\n"
        + "  S(1, 1.0);\n"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, true, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(0),
            new IdGenerator()));
    for (SimplifyExprReductionOpportunity op : ops) {
      op.applyReduction();
    }
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
