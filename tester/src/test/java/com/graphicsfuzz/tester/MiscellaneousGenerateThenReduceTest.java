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

package com.graphicsfuzz.tester;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.SameValueRandom;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public class MiscellaneousGenerateThenReduceTest {

  @Test
  public void testControlFlowWrapElimination1() throws Exception {
    checkControlFlowWrapElimination("int x; void main() { x = 2; }");
  }

  @Test
  public void testControlFlowWrapElimination3() throws Exception {
    checkControlFlowWrapElimination("int x; void main() { if (x > 0) { x = 2; } }");
  }

  @Test
  public void testControlFlowWrapElimination4() throws Exception {
    checkControlFlowWrapElimination("int x; void main() { if (x > 0) { x = 2; } else { x = 3; } }");
  }

  private void checkControlFlowWrapElimination(String program)
      throws IOException, ParseTimeoutException {
    TranslationUnit tu = Helper.parse(program, false);

    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_440;
    new AddWrappingConditionalStmts().apply(tu,
        TransformationProbabilities.onlyWrap(),
        shadingLanguageVersion,
        new SameValueRandom(false, 0),
        GenerationParams.normal(ShaderKind.FRAGMENT));

    System.out.println(PrettyPrinterVisitor.prettyPrintAsString(tu));

    while(true) {
      List<IReductionOpportunity> ops = ReductionOpportunities
          .getReductionOpportunities(tu,
                new ReductionOpportunityContext(false, shadingLanguageVersion,
              new SameValueRandom(false, 0), new IdGenerator()));
      if (ops.isEmpty()) {
        break;
      }
      ops.get(0).applyReduction();
    }

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(Helper.parse(program, false)),
      PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}
