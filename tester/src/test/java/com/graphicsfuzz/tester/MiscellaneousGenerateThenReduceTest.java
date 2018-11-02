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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.SameValueRandom;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.transformation.controlflow.AddWrappingConditionalStmts;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.reducer.reductionopportunities.IReductionOpportunity;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunities;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MiscellaneousGenerateThenReduceTest {

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

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
    TranslationUnit tu = ParseHelper.parse(program);

    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_440;
    new AddWrappingConditionalStmts().apply(tu,
        TransformationProbabilities.onlyWrap(),
        shadingLanguageVersion,
        new SameValueRandom(false, 0),
        GenerationParams.normal(ShaderKind.FRAGMENT, true));

    System.out.println(PrettyPrinterVisitor.prettyPrintAsString(tu));

    while(true) {
      List<IReductionOpportunity> ops = ReductionOpportunities
          .getReductionOpportunities(new GlslShaderJob(Optional.empty(),
                  new UniformsInfo(), tu),
                new ReducerContext(false, shadingLanguageVersion,
              new SameValueRandom(false, 0), new IdGenerator(), true), fileOps);
      if (ops.isEmpty()) {
        break;
      }
      ops.get(0).applyReduction();
    }

    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program)),
      PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

}
