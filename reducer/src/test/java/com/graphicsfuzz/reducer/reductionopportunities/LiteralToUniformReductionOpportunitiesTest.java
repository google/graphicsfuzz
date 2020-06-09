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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LiteralToUniformReductionOpportunitiesTest {

  @Test
  public void testReplaceSimpleInt() throws Exception {
      final String shader = "void main() { "
        + "int a = 1; int b = 1; int c = 2;}";

    final String shaderReplaced = "uniform int _GLF_uniform_values[2];"
        + "void main()"
        + "{"
        + "  int a = _GLF_uniform_values[0];"
        + "  int b = _GLF_uniform_values[0];"
        + "  int c = _GLF_uniform_values[1];"
        + "}";

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(shader, ShaderKind.FRAGMENT));

    final List<LiteralToUniformReductionOpportunity> ops =
        LiteralToUniformReductionOpportunities
        .findOpportunities(shaderJob,
            new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));

    assertEquals("There should be three opportunities", 3, ops.size());

    ops.forEach(AbstractReductionOpportunity::applyReduction);

    CompareAsts.assertEqualAsts(shaderReplaced, shaderJob.getFragmentShader().get());

    assertTrue(shaderJob.getPipelineInfo().hasUniform("_GLF_uniform_values"));

  }

}
