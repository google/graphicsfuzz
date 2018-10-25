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
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

public class LoopMergeReductionOpportunitiesTest {

  @Test
  public void findOpportunities() throws Exception {

    final int id = 72;

    final String loopVariable = Constants.SPLIT_LOOP_COUNTER_PREFIX + id + "c";

    final String loopBody =
          "    x = x + " + loopVariable + ";\n"
        + "    x = " + loopVariable + " + 2;\n";

    final String firstLoop =
        "  for(int " + loopVariable + " = 3; "
            + loopVariable + " < 10; "
            + "++" + loopVariable + ") {\n"
            + loopBody
            + "  }";

    final String secondLoop =
        "  for(int " + loopVariable + " = 10; "
            + loopVariable + " < 100; "
            + "++" + loopVariable + ") {\n"
            + loopBody
            + "  }\n";

    final String program =
        "void main() {\n"
            + "  int x = 4;\n"
            + firstLoop
            + secondLoop
            + "}\n";

    final TranslationUnit tu = ParseHelper.parse(program);

    List<LoopMergeReductionOpportunity> opportunities =
        LoopMergeReductionOpportunities.findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false, null, null, null, true));

    assertEquals(1, opportunities.size());

    opportunities.get(0).applyReduction();

    final String expectedProgram =
        "void main()\n"
            + "{\n"
            + "    int x = 4;\n"
            + "    for(\n"
            + "        int c = 3;\n"
            + "        c < 100;\n"
            + "        ++ c\n"
            + "    )\n"
            + "        {\n"
            + "            x = x + c;\n"
            + "            x = c + 2;\n"
            + "        }\n"
            + "}\n";

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expectedProgram)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}