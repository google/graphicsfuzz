/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiteralToUniformReductionOpportunitiesTest {

  @Test
  public void testReplaceSimpleInt() throws Exception {
    final String shader = "void main() { float a = 1; int b = 22; }";

    final String shaderReplaced = "layout(set = 0, binding = 0) uniform buf0 {"
        + " int one;"
        + "};"
        + "void main()"
        + "{"
        + "  int a = one;"
        + "}";

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(shader, ShaderKind.FRAGMENT));

    /*
    List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
        .findOpportunities(shaderJob,
            new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));
*/
   // assertEquals("There should be one opportunity", 1, ops.size());
    //ops.get(0).applyReduction();

    LiteralToUniformReductionOpportunities.replaceOpportunities(shaderJob);

    //noinspection OptionalGetWithoutIsPresent
   // CompareAsts.assertEqualAsts(shaderReplaced, shaderJob.getFragmentShader().get());

    // TODO: assert that the pipelineInfo has the new uniform using: shaderJob.getPipelineInfo().
   // assertTrue(shaderJob.getPipelineInfo().hasUniform("one"));

    //System.out.println( PrettyPrinterVisitor.prettyPrintAsString(shaderJob.getFragmentShader().get()));
    //System.out.println(shaderJob.getPipelineInfo());
  }

}
