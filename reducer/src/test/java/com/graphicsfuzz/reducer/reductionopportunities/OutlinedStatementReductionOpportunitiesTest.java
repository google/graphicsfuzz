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
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OutlinedStatementReductionOpportunitiesTest {

  @Test
  public void testOutline() throws Exception {
    String program = "int _GLF_outlined_1(int x) { return x; }"
        + "void main() { int y; y = _GLF_outlined_1(0); }";

    String programAfter = "int _GLF_outlined_1(int x) { return x; }"
        + "void main() { int y; y = 0; }";

    TranslationUnit tu = ParseHelper.parse(program);

    List<OutlinedStatementReductionOpportunity> ops = OutlinedStatementReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testOutline2() throws Exception {
    String program = "int _GLF_outlined_1(int x, int y, int z) { return x + (x + y) + z; }"
        + "void main() { int a, b, c; y = _GLF_outlined_1(0, a, b + c); }";

    String programAfter = "int _GLF_outlined_1(int x, int y, int z) { return x + (x + y) + z; }"
        + "void main() { int a, b, c; y = 0 + (0 + a) + b + c; }";

    TranslationUnit tu = ParseHelper.parse(program);

    List<OutlinedStatementReductionOpportunity> ops = OutlinedStatementReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testOutline3() throws Exception {
    String program = "uniform vec2 injectionSwitch;"
        + "int _GLF_outlined_1(int x, int y, int z) { return injectionSwitch.y * x + (x + y) + z; }"
        + "void main() { int a, b, c; y = _GLF_outlined_1(0, a, b + c); }";

    String programAfter = "uniform vec2 injectionSwitch;"
        + "int _GLF_outlined_1(int x, int y, int z) { return injectionSwitch.y * x + (x + y) + z; }"
        + "void main() { int a, b, c; y = injectionSwitch.y * 0 + (0 + a) + b + c; }";

    TranslationUnit tu = ParseHelper.parse(program);

    List<OutlinedStatementReductionOpportunity> ops = OutlinedStatementReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(1, ops.size());

    ops.get(0).applyReduction();

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(programAfter)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));

  }

  @Test
  public void testOutline4() throws Exception {
    String program = "int _GLF_outlined_1(int x, int y, int z) { if(_GLF_DEAD(false)) { } return injectionSwitch.y * x + (x + y) + z; }"
        + "void main() { int a, b, c; y = _GLF_outlined_1(0, a, b + c); }";

    TranslationUnit tu = ParseHelper.parse(program);

    List<OutlinedStatementReductionOpportunity> ops = OutlinedStatementReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));
    assertEquals(0, ops.size());

  }

}