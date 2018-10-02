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
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.SameValueRandom;
import java.util.List;
import org.junit.Test;

public class UnwrapReductionOpportunitiesTest {

  @Test
  public void testBlock1() throws Exception {
    final String program = "int x; void main() { { x = 2; } }";
    final TranslationUnit tu = Helper.parse(program, false);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReductionOpportunityContext(false,
        ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null));
    assertEquals(1, ops.size());
  }

  @Test
  public void testEmptyBlock() throws Exception {
    final String program = "void main() { { } }";
    final TranslationUnit tu = Helper.parse(program, false);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReductionOpportunityContext(false,
          ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null));
    assertEquals(0, ops.size());
  }

  @Test
  public void testNestedEmptyBlocks() throws Exception {
    final String program = "void main() { { { } } }";
    final TranslationUnit tu = Helper.parse(program, false);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu),
        new ReductionOpportunityContext(false,
            ShadingLanguageVersion.GLSL_440, new SameValueRandom(false, 0), null));
    assertEquals(1, ops.size());
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
    TranslationUnit tu = Helper.parse(shader, false);
    final String expectedStmt = "x = 1.0;";
    assertTrue(PrettyPrinterVisitor.prettyPrintAsString(tu).contains(expectedStmt));

    IRandom generator = new RandomWrapper(1);
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, generator, null));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    assertTrue(PrettyPrinterVisitor.prettyPrintAsString(tu).contains(expectedStmt));
  }

  @Test
  public void testDoubleRemoval() throws Exception {
    final String shader = "void main() { if (" + Constants.GLF_DEAD + "(false)) { { { } } } }";
    final String expected = "void main() { }";
    final TranslationUnit tu = Helper.parse(shader, false);
    final IRandom generator = new RandomWrapper(0);
    List<StmtReductionOpportunity> stmtOps = StmtReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, generator, null));
    List<UnwrapReductionOpportunity> unwrapOps = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, ShadingLanguageVersion.ESSL_100, generator, null));
    assertTrue(!stmtOps.isEmpty());
    assertTrue(!unwrapOps.isEmpty());
    stmtOps.forEach(StmtReductionOpportunity::applyReduction);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected, false)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
    // These should now be disabled so should do nothing.
    unwrapOps.forEach(UnwrapReductionOpportunity::applyReduction);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected, false)),
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
    final TranslationUnit tu = Helper.parse(shader, false);
    final ShadingLanguageVersion version = ShadingLanguageVersion.ESSL_100;
    final RandomWrapper generator = new RandomWrapper(0);
    final IdGenerator idGenerator = new IdGenerator();
    List<UnwrapReductionOpportunity> ops = UnwrapReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, version,
          generator, null));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();
    List<? extends IReductionOpportunity> remainingOps = DeclarationReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false, version, generator, null));
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected, false)));
    assertEquals(2, remainingOps.size());
    remainingOps.get(0).applyReduction();
    remainingOps.get(1).applyReduction();
    final String expected2 = "void main() {"
          + "  {"
          + "  }"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected2, false)));
    remainingOps = ReductionOpportunities.getReductionOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReductionOpportunityContext(false, version, generator, idGenerator));
    assertEquals(1, remainingOps.size());
    remainingOps.get(0).applyReduction();
    final String expected3 = "void main() {"
          + "}";
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(tu), PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(expected3, false)));
    remainingOps = ReductionOpportunities.getReductionOpportunities(MakeShaderJobFromFragmentShader.make(tu),
          new ReductionOpportunityContext(false, version, generator, idGenerator));
    assertEquals(0, remainingOps.size());
  }

}