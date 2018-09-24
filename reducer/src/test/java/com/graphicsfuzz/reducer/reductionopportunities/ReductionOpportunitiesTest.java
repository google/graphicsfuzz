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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ReductionOpportunitiesTest {

  @Test
  public void testDeadConditionalNotReplaced() throws IOException, ParseTimeoutException {
    // Regression test for a bug where the guard of a dead conditional was being replaced by "true"
    String prog = "void main() {" +
      "if(" + Constants.GLF_DEAD + "(" + Constants.GLF_IDENTITY + "(false, true && ("
        + Constants.GLF_IDENTITY + "(false, (true ? false : " + Constants.GLF_FUZED
        + "(true))))))) {"
        + "  }"
        + "}\n";

    TranslationUnit tu = Helper.parse(prog, false);
    List<IReductionOpportunity> opportunities =
        ReductionOpportunities.getReductionOpportunities(tu,
              new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
            new RandomWrapper(), new IdGenerator()));
    // There should be no ExprToConstant reduction opportunity, because the expressions do not occur
    // under dead code, and the fuzzed expression is too simple to be reduced.
    assertFalse(opportunities.stream().anyMatch(item -> item instanceof SimplifyExprReductionOpportunity));
  }

  @Test
  public void testPopScope() throws Exception {
    // Regression test added due to bug where scope popping would lead to an exception.
    String program = "void foo(int x) { x; } void main() { }\n";
    TranslationUnit tu = Helper.parse(program, false);
    ReductionOpportunities.getReductionOpportunities(tu,
          new ReductionOpportunityContext(false,
        ShadingLanguageVersion.ESSL_100,
        new RandomWrapper(), new IdGenerator()));
  }

  private void stressTestStructification(String variantProgram, String reducedProgram) throws Exception{

    TranslationUnit tu = Helper.parse(variantProgram, false);
    IRandom generator = new RandomWrapper();
    while (true) {
      List<InlineStructifiedFieldReductionOpportunity> ops
          = InlineStructifiedFieldReductionOpportunities.findOpportunities(tu,
              new ReductionOpportunityContext(false, null, null, null));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(generator.nextInt(ops.size())).applyReduction();
    }

    while (true) {
      List<DestructifyReductionOpportunity> ops
          = DestructifyReductionOpportunities.findOpportunities(tu, new ReductionOpportunityContext(false, null, null, null));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(generator.nextInt(ops.size())).applyReduction();
    }

    while (true) {
      List<IReductionOpportunity> ops
          = ReductionOpportunities.getReductionOpportunities(tu,
            new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(), new IdGenerator()));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(generator.nextInt(ops.size())).applyReduction();
    }

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(
        Helper.parse(reducedProgram, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void stressStructification1() throws Exception {
    String variantProgram = "struct _GLF_struct_18 {\n"
        + "    int z;\n"
        + "    ivec2 _f0;\n"
        + "    mat2 _f1;\n"
        + "};\n"
        + "struct _GLF_struct_13 {\n"
        + "    vec2 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_12 {\n"
        + "    bvec3 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_11 {\n"
        + "    vec3 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_14 {\n"
        + "    _GLF_struct_11 _f0;\n"
        + "    _GLF_struct_12 _f1;\n"
        + "    _GLF_struct_13 _f2;\n"
        + "};\n"
        + "struct _GLF_struct_15 {\n"
        + "    _GLF_struct_14 _f0;\n"
        + "    bool _f1;\n"
        + "};\n"
        + "struct _GLF_struct_8 {\n"
        + "    int _f0;\n"
        + "    bvec4 _f1;\n"
        + "    vec2 _f2;\n"
        + "};\n"
        + "struct _GLF_struct_7 {\n"
        + "    mat3 _f0;\n"
        + "};\n"
        + "struct _GLF_struct_9 {\n"
        + "    _GLF_struct_7 _f0;\n"
        + "    _GLF_struct_8 _f1;\n"
        + "    mat4 _f2;\n"
        + "};\n"
        + "struct _GLF_struct_5 {\n"
        + "    int _f0;\n"
        + "    mat3 _f1;\n"
        + "    bool _f2;\n"
        + "    bvec3 _f3;\n"
        + "};\n"
        + "struct _GLF_struct_6 {\n"
        + "    _GLF_struct_5 _f0;\n"
        + "    int y;\n"
        + "};\n"
        + "struct _GLF_struct_10 {\n"
        + "    _GLF_struct_6 _f0;\n"
        + "    _GLF_struct_9 _f1;\n"
        + "};\n"
        + "struct _GLF_struct_16 {\n"
        + "    _GLF_struct_10 _f0;\n"
        + "    _GLF_struct_15 _f1;\n"
        + "    int _f2;\n"
        + "    float _f3;\n"
        + "};\n"
        + "struct _GLF_struct_0 {\n"
        + "    bvec3 _f0;\n"
        + "    float _f1;\n"
        + "};\n"
        + "struct _GLF_struct_1 {\n"
        + "    mat4 _f0;\n"
        + "    _GLF_struct_0 _f1;\n"
        + "    ivec4 _f2;\n"
        + "};\n"
        + "struct _GLF_struct_2 {\n"
        + "    _GLF_struct_1 _f0;\n"
        + "    mat2 _f1;\n"
        + "};\n"
        + "struct _GLF_struct_3 {\n"
        + "    _GLF_struct_2 _f0;\n"
        + "    int x;\n"
        + "};\n"
        + "uniform vec2 injectionSwitch;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + "    _GLF_struct_3 _GLF_struct_replacement_4;\n"
        + "    _GLF_struct_replacement_4.x = 2;\n"
        + "    _GLF_struct_16 _GLF_struct_replacement_17;\n"
        + "    _GLF_struct_replacement_17._f0._f0.y = 3;\n"
        + "    _GLF_struct_18 _GLF_struct_replacement_19;\n"
        + "    _GLF_struct_replacement_19.z = _GLF_struct_replacement_4.x + _GLF_struct_replacement_17._f0._f0.y;\n"
        + "    _GLF_struct_replacement_17._f0._f0.y = _GLF_struct_replacement_19.z + _GLF_struct_replacement_4.x;\n"
        + "}\n";

    String reducedProgram = "void main() {\n"
        + "  int x;\n"
        + "  x = 2;\n"
        + "  int y;\n"
        + "  y = 3;\n"
        + "  int z;\n"
        + "  z = x + y;\n"
        + "  y = z + x;\n"
        + "}\n";

    stressTestStructification(variantProgram, reducedProgram);

  }

  @Test
  public void stressStructification2() throws Exception {

    String variantProgram = "  struct _GLF_struct_8 {\n"
        + "    mat3 _f0;\n"
        + "  };\n"
        + "  struct _GLF_struct_5 {\n"
        + "    float _f0;\n"
        + "    int _f1;\n"
        + "  };\n"
        + "  struct _GLF_struct_4 {\n"
        + "    float _f0;\n"
        + "    vec4 _f1;\n"
        + "  };\n"
        + "  struct _GLF_struct_3 {\n"
        + "    int _f0;\n"
        + "    bool _f1;\n"
        + "    ivec2 _f2;\n"
        + "    vec4 _f3;\n"
        + "    mat3 _f4;\n"
        + "  };\n"
        + "  struct _GLF_struct_6 {\n"
        + "    int y;\n"
        + "    _GLF_struct_3 _f0;\n"
        + "    _GLF_struct_4 _f1;\n"
        + "    _GLF_struct_5 _f2;\n"
        + "    ivec3 _f3;\n"
        + "  };\n"
        + "  struct _GLF_struct_7 {\n"
        + "    _GLF_struct_6 _f0;\n"
        + "    ivec2 _f1;\n"
        + "  };\n"
        + "  struct _GLF_struct_2 {\n"
        + "    mat3 _f0;\n"
        + "  };\n"
        + "  struct _GLF_struct_9 {\n"
        + "    vec3 _f0;\n"
        + "    _GLF_struct_2 _f1;\n"
        + "    _GLF_struct_7 _f2;\n"
        + "    _GLF_struct_8 _f3;\n"
        + "  };\n"
        + "  struct _GLF_struct_0 {\n"
        + "    int x;\n"
        + "    ivec3 _f0;\n"
        + "  };\n"
        + "  uniform vec2 injectionSwitch;\n"
        + "\n"
        + "  struct S {\n"
        + "    int a;\n"
        + "    int b;\n"
        + "  };\n"
        + "  void main()\n"
        + "  {\n"
        + "    _GLF_struct_0 _GLF_struct_replacement_1 = _GLF_struct_0(2, ivec3(1));\n"
        + "    _GLF_struct_9 _GLF_struct_replacement_10 = _GLF_struct_9(vec3(1.0), _GLF_struct_2(mat3(1.0)), _GLF_struct_7(_GLF_struct_6(_GLF_struct_replacement_1.x, _GLF_struct_3(1, true, ivec2(1), vec4(1.0), mat3(1.0)), _GLF_struct_4(1.0, vec4(1.0)), _GLF_struct_5(1.0, 1), ivec3(1)), ivec2(1)), _GLF_struct_8(mat3(1.0)));\n"
        + "    S z = S(_GLF_struct_replacement_1.x, _GLF_struct_replacement_10._f2._f0.y);\n"
        + "    _GLF_struct_replacement_10._f2._f0.y = z.a;\n"
        + "    z.a = _GLF_struct_replacement_1.x + _GLF_struct_replacement_10._f2._f0.y;\n"
        + "    _GLF_struct_replacement_10._f2._f0.y = z.b + _GLF_struct_replacement_1.x;\n"
        + "  }\n";

    String reducedProgram = "struct S {\n"
        + "  int a;\n"
        + "  int b;\n"
        + "};\n"
        + "\n"
        + "void main() {\n"
        + "  int x = 2;\n"
        + "  int y = x;\n"
        + "  S z = S(x, y);\n"
        + "  y = z.a;\n"
        + "  z.a = x + y;\n"
        + "  y = z.b + x;\n"
        + "}\n";

    stressTestStructification(variantProgram, reducedProgram);

  }

  @Test
  public void testStructInlineWithParam() throws Exception {
    String program = "struct _GLF_struct_1 {\n"
        + "    int x;\n"
        + "};"
        + "struct _GLF_struct_2 {\n"
        + "    _GLF_struct_1 _f0;\n"
        + "};"
        + "int getX(_GLF_struct_2 p) {"
        + "  return p._f0.x;"
        + "}"
        + " int main() {"
        + "  _GLF_struct_2 _GLF_struct_replacement_3 = _GLF_struct_2(_GLF_struct_1(0));"
        + "  getX(_GLF_struct_replacement_3);"
        + "}";

    String reducedProgram = "struct _GLF_struct_1 {\n"
        + "    int x;\n"
        + "};"
        + "struct _GLF_struct_2 {\n"
        + "    int x;\n"
        + "};"
        + "int getX(_GLF_struct_2 p) {"
        + "  return p.x;"
        + "}"
        + " int main() {"
        + "  _GLF_struct_2 _GLF_struct_replacement_3 = _GLF_struct_2(0);"
        + "  getX(_GLF_struct_replacement_3);"
        + "}";

    TranslationUnit tu = Helper.parse(program, false);

    List<InlineStructifiedFieldReductionOpportunity> ops =
        InlineStructifiedFieldReductionOpportunities.findOpportunities(tu, new ReductionOpportunityContext(false, null, null, null));
        InlineStructifiedFieldReductionOpportunities.findOpportunities(tu, new ReductionOpportunityContext(false, null, null, null));

    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    assertEquals(
        PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(reducedProgram, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testDestructifyWithParam() throws Exception {
    String program = "struct _GLF_struct_1 {\n"
        + "    int x;\n"
        + "};"
        + "struct _GLF_struct_2 {\n"
        + "    _GLF_struct_1 _f0;\n"
        + "};"
        + "int getX(_GLF_struct_2 _GLF_struct_replacement_3) {"
        + "  return _GLF_struct_replacement_3._f0.x;"
        + "}"
        + " int main() {"
        + "  _GLF_struct_2 _GLF_struct_replacement_3 = _GLF_struct_2(_GLF_struct_1(0));"
        + "  getX(_GLF_struct_replacement_3);"
        + "}";

    String reducedProgram = "struct _GLF_struct_1 {\n"
        + "    int x;\n"
        + "};"
        + "struct _GLF_struct_2 {\n"
        + "    _GLF_struct_1 _f0;\n"
        + "};"
        + "int getX(int x) {"
        + "  return x;"
        + "}"
        + " int main() {"
        + "  int x = 0;"
        + "  getX(x);"
        + "}";

    TranslationUnit tu = Helper.parse(program, false);

    List<DestructifyReductionOpportunity> ops =
        DestructifyReductionOpportunities.findOpportunities(tu,
              new ReductionOpportunityContext(false, null, null, null));

    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    assertEquals(
        PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(reducedProgram, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void exprSimplificationAndLoopSplitInteraction() throws Exception {

    String program = "void main() {"
        + "  if(_GLF_DEAD(_GLF_FALSE(false, false))) {"

        // The expressions in here can now be reduced to constants, and there was an issue where
        // this was affecting loop merging.

        + "        for(\n"
        + "            int _GLF_SPLIT_LOOP_COUNTER_1n = 0;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n < 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n ++\n"
        + "        )\n"
        + "            {\n"
        + "            }\n"
        + "        for(\n"
        + "            int _GLF_SPLIT_LOOP_COUNTER_1n = 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n < 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n ++\n"
        + "        )\n"
        + "            {\n"
        + "            }\n"
        + "  }\n"
        + "}\n";


    TranslationUnit tu = Helper.parse(program, false);

    List<Integer> indicesOfExprToConstOps = new ArrayList<>();
    {
      List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(tu,
            new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
      for (int i = 0; i < ops.size(); i++) {
        if (ops.get(i) instanceof SimplifyExprReductionOpportunity) {
          indicesOfExprToConstOps.add(i);
        }
      }
    }

    // Check that applying an ExprToConstant followed by all loop merges does not fail.

    for (Integer index : indicesOfExprToConstOps) {
      TranslationUnit aClone = tu.clone();
      {
        List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(
              aClone,
              new ReductionOpportunityContext(false,
            ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));
        ((SimplifyExprReductionOpportunity) ops.get(index)).applyReduction();
      }
      for (IReductionOpportunity op : ReductionOpportunities.getReductionOpportunities(aClone,
            new ReductionOpportunityContext(false,
          ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()))) {
        if (op instanceof LoopMergeReductionOpportunity) {
          op.applyReduction();
        }
      }
    }

  }

  @Test
  public void loopSplitCompatibility() throws Exception {
    String program = "void main() {"
        + "  if(_GLF_DEAD(_GLF_FALSE(false, false))) {"

        // The expressions in here can now be reduced to constants, and there was an issue where
        // this was affecting loop merging.

        + "        for(\n"
        + "            int _GLF_SPLIT_LOOP_COUNTER_1n = 0;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n < 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n ++\n"
        + "        )\n"
        + "            {\n"
        + "            }\n"
        + "        for(\n"
        + "            int _GLF_SPLIT_LOOP_COUNTER_1n = 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n < 3;\n"
        + "            _GLF_SPLIT_LOOP_COUNTER_1n ++\n"
        + "        )\n"
        + "            {\n"
        + "            }\n"
        + "  }\n"
        + "}\n";

    tryAllCompatibleOpportunities(program);

  }

  @Test
  public void outlineCompatibility() throws Exception {
    String program = ""
        + "int _GLF_outlined_1(int x) {"
        + "  return x;"
        + "}"
        + "void main() {"
        + "  if(_GLF_DEAD(_GLF_FALSE(false, false))) {"
        + "    int x;"
        + "    int y = 2;"
        + "    x = _GLF_outlined_1(y);"
        + "  }\n"
        + "}\n";

    tryAllCompatibleOpportunities(program);

  }

  private void tryAllCompatibleOpportunities(String program)
      throws IOException, ParseTimeoutException {
    TranslationUnit tu = Helper.parse(program, false);
    int numOps = ReductionOpportunities.getReductionOpportunities(tu,
          new ReductionOpportunityContext(false,
        ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator())).size();

    for (int i = 0; i < numOps; i++) {
      for (int j = 0; j < numOps; j++) {
        if (i == j) {
          continue;
        }
        TranslationUnit aClone = tu.clone();
        List<IReductionOpportunity> ops =
            ReductionOpportunities.getReductionOpportunities(aClone,
                  new ReductionOpportunityContext(false,
                ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), new IdGenerator()));

        if (Compatibility.compatible(ops.get(i).getClass(), ops.get(j).getClass())) {
          ops.get(i).applyReduction();
          ops.get(j).applyReduction();
        }
      }
    }
  }

  @Test
  public void reduceRedundantStuff() throws Exception {
    final String program = "void main() { int a; a = a; }";
    final String expected = "void main() { }";
    final TranslationUnit tu = Helper.parse(program, false);
    while (true) {
      List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(
            tu, new ReductionOpportunityContext(false, ShadingLanguageVersion.GLSL_440,
            new RandomWrapper(0), new IdGenerator()));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(0).applyReduction();
    }
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testReduceToConstantInLiveCode() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() {"
          + "  float GLF_live3x = sin(4.0);"
          + "  float GLF_live3y = GLF_live3x + GLF_live3x;"
          + "}", false);
    List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities.findOpportunities(tu, new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null));
    assertEquals(4, ops.size());
  }

  @Test
  public void testLeaveLoopLimiter() throws Exception {
    TranslationUnit tu = ParseHelper.parse(""
          + "void main() {"
          + "    int GLF_live3_looplimiter0 = 0;\n"
          + "    for(\n"
          + "      float GLF_live3sphereNo = 0.0;\n"
          + "      GLF_live3sphereNo < 10.0;\n"
          + "      GLF_live3sphereNo ++\n"
          + "  )\n"
          + "   {\n"
          + "    if(GLF_live3_looplimiter0 >= 5)\n"
          + "     {\n"
          + "      break;\n"
          + "     }\n"
          + "    GLF_live3_looplimiter0 ++;\n"
          + "  }"
          + "}\n", false);
    while (true) {
      List<IReductionOpportunity> ops = ReductionOpportunities.getReductionOpportunities(tu,
            new ReductionOpportunityContext(false,
            ShadingLanguageVersion.GLSL_440, new RandomWrapper(), new IdGenerator()));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(0).applyReduction();
    }

    final String expected = "void main() {"
          + "    int GLF_live3_looplimiter0 = 0;\n"
          + "    for(\n"
          + "      float GLF_live3sphereNo = 0.0;\n"
          + "      1.0 < 10.0;\n"
          + "      GLF_live3sphereNo ++\n"
          + "  )\n"
          + "   {\n"
          + "    if(GLF_live3_looplimiter0 >= 5)\n"
          + "     {\n"
          + "      break;\n"
          + "     }\n"
          + "    GLF_live3_looplimiter0 ++;\n"
          + "  }"
          + "}\n";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testTernary() throws Exception {
    final String program = "void main() {"
          + "  int a = 2, b = 3, c = 4;"
          + "  (a < b ? c : b);"
          + "}";
    int i = 0;
    while (true) {
      final TranslationUnit tu = Helper.parse(program, false);
      List<SimplifyExprReductionOpportunity> ops = ExprToConstantReductionOpportunities
            .findOpportunities(tu,
                  new ReductionOpportunityContext(true, ShadingLanguageVersion.ESSL_100,
                  new RandomWrapper(0), null));
      if (i >= ops.size()) {
        break;
      }
      ops.get(i).applyReduction();
      i++;
    }

  }

  @Test
  public void testRemoveLoopLimiter() throws Exception {
    final String program = "void main() {"
          + " {\n"
          + "  int GLF_live10_looplimiter0 = 0;\n"
          + "  for(\n"
          + "      int GLF_live10i = 0;\n"
          + "      GLF_live10i < 30;\n"
          + "      GLF_live10i ++\n"
          + "  )\n"
          + "   {\n"
          + "    if(GLF_live10_looplimiter0 >= 6)\n"
          + "     {\n"
          + "      break;\n"
          + "     }\n"
          + "    GLF_live10_looplimiter0 ++;\n"
          + "   }\n"
          + " }"
          + "}";
    final String expected = "void main() {"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program, false);
    List<? extends IReductionOpportunity> ops = StmtReductionOpportunities.findOpportunities(
          tu, new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100,
          new RandomWrapper(0), null));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected, false)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}