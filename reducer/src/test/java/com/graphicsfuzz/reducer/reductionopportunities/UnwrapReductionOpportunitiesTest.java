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
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.SameValueRandom;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UnwrapReductionOpportunitiesTest {

  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Test
  public void testBlock1() throws Exception {
    final String program = "int x; void main() { { x = 2; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false,
        ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    assertEquals(1, ops.size());
  }

  @Test
  public void testEmptyBlock() throws Exception {
    final String program = "void main() { { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false,
          ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    // No opportunities because the inner block is empty.  Another reduction pass may be able to
    // delete it, but it cannot be unwrapped.
    assertEquals(0, ops.size());
  }

  @Test
  public void testNestedEmptyBlocks() throws Exception {
    final String program = "void main() { { { } } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    assertEquals(1, ops.size());
  }

  @Test
  public void testUnwrapWithDeclNoClash() throws Exception {
    final String program = "void main() { int x; { int y; } }";
    final String expected = "void main() { int x; int y; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testNoUnwrapWithDeclClash() throws Exception {
    final String program = "void main() { int x; { { int x; } x = 2; } }";
    final String expected = "void main() { int x; { int x; } x = 2; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    // The inner block cannot be unwrapped as it would change which variable 'x = 2' refers to.
    assertEquals(1, ops.size());
  }

  @Test
  public void testNoUnwrapWithDeclClash2() throws Exception {
    final String program = "void main() { { int x; } float x; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    assertEquals(0, ops.size());
  }

  @Test
  public void testOneUnwrapDisablesAnother() throws Exception {
    final String program = "void main() { { int x; } { float x; } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null, true));
    assertEquals(2, ops.size());
    assertTrue(ops.get(0).preconditionHolds());
    assertTrue(ops.get(1).preconditionHolds());
    ops.get(0).applyReduction();
    assertFalse(ops.get(1).preconditionHolds());
  }

  @Test
  public void misc() throws Exception {
    final String shader = "void main()\n"
          + "{\n"
          + "    float x;\n"
          + "    for(\n"
          + "        int i = 0;\n"
          + "        _GLF_IDENTITY(i, i) < 10;\n"
          + "        i ++\n"
          + "    )\n"
          + "        {\n"
          + "            {\n"
          + "                if(_GLF_DEAD(false))\n"
          + "                    {\n"
          + "                    }\n"
          + "                x = 1.0;\n"
          + "            }\n"
          + "        }\n"
          + "}\n";
    TranslationUnit tu = ParseHelper.parse(shader);
    final String expectedStmt = "x = 1.0;";
    assertTrue(PrettyPrinterVisitor.prettyPrintAsString(tu).contains(expectedStmt));

    IRandom generator = new RandomWrapper(1);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100, generator, null, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertTrue(PrettyPrinterVisitor.prettyPrintAsString(tu).contains(expectedStmt));
  }

  @Test
  public void testDoubleRemoval() throws Exception {
    final String shader = "void main() { if (" + Constants.GLF_DEAD + "(false)) { { { } } } }";
    final String expected = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final IRandom generator = new RandomWrapper(0);
    List<StmtReductionOpportunity> stmtOps = StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100, generator, null, true));
    List<UnwrapReductionOpportunity> unwrapOps = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, ShadingLanguageVersion.ESSL_100, generator, null, true));
    assertTrue(!stmtOps.isEmpty());
    assertTrue(!unwrapOps.isEmpty());
    stmtOps.forEach(StmtReductionOpportunity::applyReduction);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
    // These should now be disabled so should do nothing.
    unwrapOps.forEach(UnwrapReductionOpportunity::applyReduction);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void unwrapFor() throws Exception {
    final String shader = "void main() {"
          + "  int a;"
          + "  for(_injected_loop_counter_0 = 0;"
          + "      " + Constants.GLF_WRAPPED_LOOP + "(_injected_loop_counter_0 < 1);"
          + "      _injected_loop_counter_0++) {"
          + "    int a;"
          + "  }"
          + "}";
    final String expected = "void main() {"
          + "  int a;"
          + "  {"
          + "    int a;"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    final RandomWrapper generator = new RandomWrapper(0);
    final IdGenerator idGenerator = new IdGenerator();
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, version,
          generator, idGenerator, true));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    List<? extends IReductionOpportunity> remainingOps =
        VariableDeclReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, version, generator, idGenerator, true));
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)));
    assertEquals(2, remainingOps.size());
    remainingOps.get(0).applyReduction();
    remainingOps.get(1).applyReduction();
    final String expected2 = "void main() {"
        + "  int ;"
        + "  {"
        + "    int ;"
        + "  }"
        + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected2)));
    remainingOps =
        StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, version, generator, idGenerator, true));
    assertEquals(3, remainingOps.size());
    remainingOps.get(0).applyReduction();
    remainingOps.get(1).applyReduction();
    remainingOps.get(2).applyReduction();
    final String expected3 = "void main() {"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu),
        PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected3)));
    remainingOps = ReductionOpportunities.getReductionOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, version, generator, idGenerator, true), fileOps);
    assertEquals(0, remainingOps.size());
  }

}
