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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.util.Constants;
import java.util.List;
import org.junit.Test;

public class StmtReductionOpportunitiesTest {

  @Test
  public void testSwitch1() throws Exception {
    final String prog = "void main() {"
        + "  int y = 2;"
        + "  switch(_GLF_SWITCH(0)) {"
        + "    case 5:"
        + "    y += 1;"
        + "    case 6:"
        + "    case 7:"
        + "    y += 2;"
        + "    case 0:"
        + "    case 1:"
        + "    y += 3;"
        + "    case 42:"
        + "    y += 4;"
        + "    break;"
        + "    case 16:"
        + "    y += 5;"
        + "    default:"
        + "    1;"
        + "  }"
        + "}";

    final String expectedProg = "void main() {"
        + "  int y = 2;"
        + "  switch(_GLF_SWITCH(0)) {"
        + "    case 5:"
        + "    case 0:"
        + "    case 1:" // TODO: get rid of this; doesn't affect semantics.
        + "    y += 3;"
        + "    case 42:" // TODO: get rid of this; doesn't affect semantics.
        + "    y += 4;"
        + "    break;"
        + "    1;" // This gets left over because it used to be the only statement immediately
        // following a case label; the reducer conservatively decides not to remove it.  Now that
        // it does not follow a case label it could be culled by the reducer in the future.
        + "  }"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(prog);
    List<StmtReductionOpportunity> ops = StmtReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
            new ReducerContext(false, ShadingLanguageVersion.GLSL_130,
        new RandomWrapper(0), new IdGenerator()));

    for (StmtReductionOpportunity op : ops) {
      op.applyReduction();
    }

    CompareAsts.assertEqualAsts(expectedProg, tu);

  }

  @Test
  public void testDoNotLeaveDefaultEmpty() throws Exception {
    final String prog = "void main() {"
        + "  switch(0) {"
        + "    default:"
        + "    1;"
        + "  }"
        + "}";

    final String expected = "void main() {"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(prog);
    List<StmtReductionOpportunity> ops = StmtReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(true,
            ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));

    assertEquals(1, ops.size());
    ops.get(0).applyReduction();

    CompareAsts.assertEqualAsts(expected, tu);

  }

  @Test
  public void testEmptyBlock() throws Exception {
    final String program = "void main() {"
          + "  {"
          + "  }"
          + "}";
    final String reducedProgram = "void main() {"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
                new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    assertTrue(ops.get(0) instanceof StmtReductionOpportunity);
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(reducedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testNullStmtRemoved() throws Exception {
    final String program = "void main() { ; }";
    final String reducedProgram = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    assertTrue(ops.get(0) instanceof StmtReductionOpportunity);
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(reducedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testNullStmtsInForNotTouched() throws Exception {
    final String program = "void main() { for(int i = 0; i < 100; i++) ; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0),
                  new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testNullStmtsInForNotTouched2() throws Exception {
    final String program = "int x; void foo() { x = 42; } void main() { if (foo()) ; else ; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0),
                  new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testSimpleLiveCodeRemoval() throws Exception {
    final String program = "void main() {"
          + "  int GLF_live0c;"
          + "  GLF_live0c = GLF_live0c + 1;"
          + "}";
    final String reducedProgram = "void main() {"
          + "  int GLF_live0c;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    assertTrue(ops.get(0) instanceof StmtReductionOpportunity);
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(reducedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testUnaryIncDecLiveCodeRemoval() throws Exception {
    final String program = "void main() {"
          + "  int GLF_live0c;"
          + "  GLF_live0c++;"
          + "  ++GLF_live0c;"
          + "  GLF_live0c--;"
          + "  --GLF_live0c;"
          + "}";
    final String reducedProgram = "void main() {"
          + "  int GLF_live0c;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), new IdGenerator()));
    assertEquals(4, ops.size());
    for (int i = 0; i < ops.size(); i++) {
      assertTrue(ops.get(i) instanceof StmtReductionOpportunity);
      ops.get(i).applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(reducedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testSideEffectFreeTypeInitializerRemoved() throws Exception {
    final String program = "void main() { vec4(0.0, 0.0, 0.0, 0.0); }";
    final String reducedProgram = "void main() { }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    assertTrue(ops.get(0) instanceof StmtReductionOpportunity);
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(reducedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testSideEffectingTypeInitializerNotRemoved() throws Exception {
    final String program = "void main() { float x; vec4(0.0, x++, 0.0, 0.0); }";
    final TranslationUnit tu = ParseHelper.parse(program);
    List<? extends IReductionOpportunity> ops =
          StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
              new ReducerContext(false, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testDeadStatementsPickedUp() throws Exception {
    final String program = "float GLF_dead8h_r;\n"
          + "\n"
          + "float GLF_dead8s_g;\n"
          + "\n"
          + "float GLF_dead8b_b;\n"
          + "\n"
          + "void GLF_dead8doConvert()\n"
          + "{\n"
          + " vec3 GLF_dead8temp;\n"
          + " GLF_dead8temp = GLF_dead8b_b * (1.0 - GLF_dead8s_g) + (GLF_dead8b_b - GLF_dead8b_b * "
                + "(1.0 - GLF_dead8s_g)) * clamp(abs(abs(6.0 * (GLF_dead8h_r - vec3(0, 1, 2) / "
                + "3.0)) - 3.0) - 1.0, 0.0, 1.0);\n"
          + " GLF_dead8h_r = GLF_dead8temp.x;\n"
          + " GLF_dead8s_g = GLF_dead8temp.y;\n"
          + " GLF_dead8b_b = GLF_dead8temp.z;\n"
          + "}\n"
          + "\n"
          + "void main() {\n"
          + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
          MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, ShadingLanguageVersion.ESSL_300,
              new RandomWrapper(0),
              new IdGenerator()));
    assertEquals(4, ops.size());
  }

  @Test
  public void testDeadBasicReturnsPickedUp() throws Exception {
    final String program = "void GLF_dead1_f() {\n"
          + "  if (true) {"
          + "    return;"
          + "  }"
          + "  return;"
          + "}"
          + "void main() {"
          + "  if (false) {"
          + "    GLF_dead1_f();"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
          MakeShaderJobFromFragmentShader.make(tu),
          new ReducerContext(false, ShadingLanguageVersion.ESSL_300,
              new RandomWrapper(0), new IdGenerator()));
    assertEquals(3, ops.size());
  }

  @Test
  public void testRemoveDuplicateReturn1() throws Exception {
    final String program = "int foo() { return 0; return 1; return 2; return 3; }";
    final String expected = "int foo() { return 1; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final ReducerContext context = new ReducerContext(true, ShadingLanguageVersion.ESSL_300,
        new RandomWrapper(0),
        new IdGenerator());
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(tu);
    List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertEquals(4, ops.size());
    ops.get(0).applyReduction();
    ops.get(3).applyReduction();
    ops.get(2).applyReduction();
    assertFalse(ops.get(1).preconditionHolds());
    CompareAsts.assertEqualAsts(expected, tu);
    ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testRemoveDuplicateReturn2() throws Exception {
    final String program = "int foo() {\n"
        + " int x;\n"
        + " return 0;\n"
        + " x = 1;\n"
        + " return 1;\n"
        + " x = 2;\n"
        + " return 2;\n"
        + " return 3;\n"
        + "}\n";
    final String expected = "int foo() {\n"
        + " int x;\n"
        + " return 3;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, ShadingLanguageVersion.ESSL_300, new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(6, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();
    ops.get(2).applyReduction();
    ops.get(3).applyReduction();
    ops.get(4).applyReduction();
    assertFalse(ops.get(5).preconditionHolds());
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testRemoveArbitraryVoidReturn() throws Exception {
    final String program = "void main() { int x; if (x < 3) { return; } else { return; } "
        + "return; return; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, ShadingLanguageVersion.ESSL_300,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(5, ops.size());
  }

  @Test
  public void testRemoveNonVoidReturn() throws Exception {
    // Both return statements are candidates for removal, but removing one disables removing the
    // other.
    final String program = "int foo() { return 1; return 0; }";
    final String expected = "int foo() { return 0; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    final ReducerContext context = new ReducerContext(true, ShadingLanguageVersion.ESSL_300,
        new RandomWrapper(0), new IdGenerator());
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(tu);
    List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected, tu);
    assertFalse(ops.get(1).preconditionHolds());
    ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testRemoveNonVoidDeadReturn() throws Exception {
    // The return in the dead conditional is a candidate for removal.
    final String program = "int foo() {\n"
        + "  if ( " + Constants.GLF_DEAD + "(false)) {\n"
        + "    return 0;\n"
        + "  }\n"
        + "  {\n"
        + "    return 0;\n"
        + "  }\n"
        + "}\n";
    final String expected1 = "int foo() {\n"
        + "  if ( " + Constants.GLF_DEAD + "(false)) {\n"
        + "  }\n"
        + "  {\n"
        + "    return 0;\n"
        + "  }\n"
        + "}\n";
    final String expected2 = "int foo() {\n"
        + "  {\n"
        + "    return 0;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final ReducerContext context = new ReducerContext(true, ShadingLanguageVersion.ESSL_300,
        new RandomWrapper(0), new IdGenerator());
    final ShaderJob shaderJob = MakeShaderJobFromFragmentShader.make(tu);
    List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertEquals(2, ops.size());
    ops.get(1).applyReduction();
    CompareAsts.assertEqualAsts(expected1, tu);
    ops.get(0).applyReduction();
    CompareAsts.assertEqualAsts(expected2, tu);
    ops = StmtReductionOpportunities.findOpportunities(
        shaderJob,
        context);
    assertTrue(ops.isEmpty());
  }

  @Test
  public void testDoNotRemoveReturnThatWouldChangeSemantics() throws Exception {
    final String program = "layout(location = 0) out vec4 color;\n"
        + "void main() {\n"
        + "  return;\n"
        + "  color = vec4(1.0);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false, ShadingLanguageVersion.ESSL_300, new RandomWrapper(0),
            new IdGenerator()));
    // We cannot remove the return, as this would make the write to 'color' reachable.
    // We could in principle remove the color write itself, since it is unreachable.  Right now we
    // do not, so this test would have to be re-thought if we decided to add that facility.
    assertEquals(0, ops.size());
  }

  @Test
  public void testRemoveReturnsInIf() throws Exception {
    final String program = "layout(location = 0) out vec4 color;\n"
        + "int foo() {\n"
        + "  if (true) {\n"
        + "    return 1;\n"
        + "  } else {\n"
        + "    return 2;\n"
        + "  }\n"
        + "  return 0;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(true, ShadingLanguageVersion.ESSL_300, new RandomWrapper(0),
            new IdGenerator()));
    assertTrue(ops.size() > 0);
  }

  @Test
  public void testIdentifyLiveCodeProperly() throws Exception {
    final String liveInjectedVariableName = Constants.LIVE_PREFIX + "somevar";
    final String nonLiveInjectedVariableName = "x";
    final String program = ""
        + "void main() {"
        + "  float x;"
        + "  float " + liveInjectedVariableName + ";\n"
        + "  " + liveInjectedVariableName + " = 2.0;\n"
        + "  " + nonLiveInjectedVariableName + " = " + Constants.GLF_FUZZED + "("
        + liveInjectedVariableName + ");\n"
        + "  " + liveInjectedVariableName + " += 2.0;\n"
        + "  " + nonLiveInjectedVariableName + " += 2.0;\n"
        + "  " + liveInjectedVariableName + " *= 2.0;\n"
        + "  " + nonLiveInjectedVariableName + " *= 2.0;\n"
        + "  " + liveInjectedVariableName + " -= 2.0;\n"
        + "  " + nonLiveInjectedVariableName + " -= 2.0;\n"
        + "  " + liveInjectedVariableName + " /= 2.0;\n"
        + "  " + nonLiveInjectedVariableName + " /= 2.0;\n"
        + "  " + liveInjectedVariableName + "++;\n"
        + "  " + nonLiveInjectedVariableName + "++;\n"
        + "  " + liveInjectedVariableName + "--;"
        + "  " + nonLiveInjectedVariableName + "--;\n"
        + "  ++" + liveInjectedVariableName + ";\n"
        + "  ++" + nonLiveInjectedVariableName + ";\n"
        + "  --" + liveInjectedVariableName + ";\n"
        + "  --" + nonLiveInjectedVariableName + ";\n"
        // Should not be identified as live, even though it contains live stuff
        // These are all live:
        + "  {"
        + "    for (" + liveInjectedVariableName + " = 1.0; ; ) { }\n"
        + "    for (" + nonLiveInjectedVariableName + " = 1.0; " + liveInjectedVariableName + " < "
        + nonLiveInjectedVariableName + "; ) { }\n"
        + "    for (" + nonLiveInjectedVariableName + " = 1.0; " + nonLiveInjectedVariableName
                + " < " + nonLiveInjectedVariableName + "; " + liveInjectedVariableName + ") { }\n"
        + "    while (" + liveInjectedVariableName + " < 10.0) { }\n"
        + "    do { } while (" + liveInjectedVariableName + " < 10.0);\n"
        + "    switch (int(" + liveInjectedVariableName + ")) { default: break; }\n"
        // None of these are live
        + "    for (" + nonLiveInjectedVariableName + " = 1.0; ; ) { }"
        + "    for (" + nonLiveInjectedVariableName + " = 1.0; " + nonLiveInjectedVariableName
                + " < " + nonLiveInjectedVariableName + "; ) { }\n"
        + "    for (" + nonLiveInjectedVariableName + " = 1.0; " + nonLiveInjectedVariableName
                + " < " + nonLiveInjectedVariableName + "; "
                + nonLiveInjectedVariableName + ") { }\n"
        + "    while (" + nonLiveInjectedVariableName + " < 10.0) { }\n"
        + "    do { } while (" + nonLiveInjectedVariableName + " < 10.0);\n"
        + "    switch (int(" + nonLiveInjectedVariableName + ")) { default: break; }\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<Stmt> stmtsInMain = tu.getMainFunction().getBody().getStmts();
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(0)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(1)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(2)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(3)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(4)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(5)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(6)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(7)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(8)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(9)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(10)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(11)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(12)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(13)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(14)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(15)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(16)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(17)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(18)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(19)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInMain.get(20)));

    List<Stmt> stmtsInBlock = ((BlockStmt) stmtsInMain.get(20)).getStmts();
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(0)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(1)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(2)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(3)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(4)));
    assertTrue(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(5)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(6)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(7)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(8)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(9)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(10)));
    assertFalse(StmtReductionOpportunities.isLiveCodeInjection(stmtsInBlock.get(11)));

  }

  private String loopLimiter(int counter) {
    return Constants.LIVE_PREFIX + Constants.LOOP_LIMITER + counter;
  }

  private String liveVariable(String name) {
    return Constants.LIVE_PREFIX + name;
  }

  @Test
  public void testRemovalOfBlocksThatUseLoopLimiters() throws Exception {
    final String program = ""
        + "void main() {\n"
        + "  int " + loopLimiter(1) + " = 0;\n"
        + "  for (int " + liveVariable("x") + " = 0; ; " + liveVariable("x") + "++) {\n"
        + "    for (int " + Constants.INJECTED_LOOP_COUNTER + " = 0; "
                + Constants.INJECTED_LOOP_COUNTER + " < 1; " + Constants.INJECTED_LOOP_COUNTER
                + "++) {\n"
        + "      if (" + loopLimiter(1) + " >= 5) {\n"
        + "        break;\n"
        + "      }\n"
        + "      if (true) {\n"
        + "        " + loopLimiter(1) + "++;\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops =
        StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false, ShadingLanguageVersion.ESSL_310,
            new RandomWrapper(0), new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();

    final String expected = ""
        + "void main() {\n"
        + "  int " + loopLimiter(1) + " = 0;\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testGetRidOfLeftOverLoopLimiterReferences1() throws Exception {
    final String loopLimiterName = loopLimiter(0);
    final String program = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName + " = 0;\n"
        + "  " + loopLimiterName + "++;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false, ShadingLanguageVersion.ESSL_310, new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();

    final String expected = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName + " = 0;\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testGetRidOfLeftOverLoopLimiterReferences2() throws Exception {
    final String loopLimiterName = loopLimiter(0);
    final String program = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName + " = 0;\n"
        + "  if (" + loopLimiterName + ">= 5) {\n"
        + "    " + loopLimiterName + "++;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false, ShadingLanguageVersion.ESSL_310, new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();

    final String expected = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName + " = 0;\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testGetRidOfLeftOverLoopLimiterReferences3() throws Exception {
    final String loopLimiterName1 = loopLimiter(0);
    final String loopLimiterName2 = loopLimiter(1);
    final String program = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName1 + " = 0;\n"
        + "  while (true) {\n"
        + "    int " + loopLimiterName2 + " = 0;\n"
        + "    if (" + loopLimiterName2 + ">= 5) {\n"
        + "      " + loopLimiterName2 + "++;\n"
        + "    }\n"
        + "    if (" + loopLimiterName1 + " >= 3) {\n"
        + "      break;\n"
        + "    }\n"
        + "    " + loopLimiterName1 + "++;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<StmtReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
        MakeShaderJobFromFragmentShader.make(tu),
        new ReducerContext(false, ShadingLanguageVersion.ESSL_310, new RandomWrapper(0),
            new IdGenerator()));
    assertEquals(2, ops.size());
    ops.get(0).applyReduction();
    ops.get(1).applyReduction();

    final String expected = ""
        + "#version 310 es\n"
        + "void main() {\n"
        + "  int " + loopLimiterName1 + " = 0;\n"
        + "  while (true) {\n"
        + "    int " + loopLimiterName2 + " = 0;\n"
        + "    if (" + loopLimiterName1 + " >= 3) {\n"
        + "      break;\n"
        + "    }\n"
        + "    " + loopLimiterName1 + "++;\n"
        + "  }\n"
        + "}\n";
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
