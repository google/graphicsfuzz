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
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.util.List;
import org.junit.Test;

public class InlineInitializerReductionOpportunitiesTest {

  @Test
  public void testGlobalScope() throws Exception {
    final String program = "const int x = 2; void main() { int y = x + 3; }";
    final String expected = "const int x = 2; void main() { int y = (2) + 3; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
              .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
          true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);

    // If we are preserving semantics, we do not want to apply this transformation.
    assertTrue(InlineInitializerReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
        true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator())).isEmpty());
  }

  @Test
  public void testDoNotInlineLargeInitializer() throws Exception {

    final String largeProgram = make1Plus1Plus1Program(100);
    final String smallProgram = make1Plus1Plus1Program(5);
    final String smallProgramInlined = make1Plus1Plus1ProgramInlined(5);

    final TranslationUnit largeProgramTu = ParseHelper.parse(largeProgram);
    final TranslationUnit smallProgramTu = ParseHelper.parse(smallProgram);

    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(largeProgramTu),
                new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());

    ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(smallProgramTu),
                new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(3, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();
    ops.get(2).applyReduction();
    CompareAsts.assertEqualAsts(smallProgramInlined, smallProgramTu);

  }

  private String make1Plus1Plus1Program(int n) {
    return "void main() { int i = " + make1Plus1Plus1Expr(n) + "; i; i; i; }";
  }

  private String make1Plus1Plus1ProgramInlined(int n) {
    return "void main() { int i = " + make1Plus1Plus1Expr(n) + "; (" + make1Plus1Plus1Expr(n)
        + "); (" + make1Plus1Plus1Expr(n) + "); (" + make1Plus1Plus1Expr(n) + "); }";
  }

  private String make1Plus1Plus1Expr(int n) {
    String expr = "1";
    for (int i = 0; i < n - 1; i++) {
      expr += " + 1";
    }
    return expr;
  }

  @Test
  public void testDoNotInlineWhenPreservingSemantics() throws Exception {

    final String program = "void main() { int i = 4 + 2; i += i + i + i; i -= i * i; }";

    final TranslationUnit tu = ParseHelper.parse(program);

    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testDoNotInlineWhenPreservingSemantics2() throws Exception {

    final String program = "void main() { int i = 4 + 2; i += i + i + i; i -= i * i; }";

    final TranslationUnit tu = ParseHelper.parse(program);

    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testDoNotInlineWhenPreservingSemantics3() throws Exception {
    final String program  = "void main() { int x = 2; int y = x; x = 3; y; }";
    final TranslationUnit tu = ParseHelper.parse(program);

    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testNoInliningWithNameShadowing1() throws Exception {
    // We don't want to inline the "int x = x" initializer as it will lead to an identical program.
    final String program  = "void main() { int x; { int x = x; x; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testNoInliningWithNameShadowing2() throws Exception {
    // We don't want to inline the "int x = a" as it will lead to a name clash that will cause
    // a type error.
    final String program  = "void main() { int a; int x = a; { float a; int k; k = x; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testInliningInDeadCodeWithPreserveSemantics() throws Exception {
    // We do want to be able to inline an initializer if we are in dead code, when preserving
    // semantics
    final String program = "void main() {\n"
        + "  if(" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false, false))) {\n"
        + "    int do_inline_me = 5 + 2;\n"
        + "    do_inline_me * do_inline_me;\n"
        + "  }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + "  if(" + Constants.GLF_DEAD + "(" + Constants.GLF_FALSE + "(false, false))) {\n"
        + "    int do_inline_me = 5 + 2;\n"
        + "    (5 + 2) * (5 + 2);\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testInliningInLiveCodeWithPreserveSemantics() throws Exception {
    // We do want to be able to inline an initializer if we are in live code, when preserving
    // semantics
    final String variableName = Constants.LIVE_PREFIX + "do_inline_me";
    final String program = "void main() {\n"
        + "  {\n"
        + "    int " + variableName + " = 5 + 2;\n"
        + "    " + variableName + " * " + variableName + ";\n"
        + "  }\n"
        + "}\n";
    final String expected = "void main() {\n"
        + "  {\n"
        + "    int " + variableName + " = 5 + 2;\n"
        + "    (5 + 2) * (5 + 2);\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<SimplifyExprReductionOpportunity> ops = InlineInitializerReductionOpportunities
            .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            true, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
