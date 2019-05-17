/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RemoveRedundantUniformMetadataReductionOpportunitiesTest {

  // TODO(478): enable this test once the issue is addressed.
  @Ignore
  @Test
  public void testRemoveUnused() throws Exception {
    // Make a shader job with an empty fragment shader, and declare one uniform in the pipeline
    // state such that the uniform is not used.
    final String emptyShader = "void main() { }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("unused", BasicType.FLOAT, Optional.empty(), Collections.singletonList(10.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(emptyShader));
    // Check that initially there is indeed one uniform in the pipeline state.
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be exactly one opportunity to remove a piece of unused pipeline state.
    // TODO(478): remove the Assert.fail(), and un-comment the lines that follow it.
    Assert.fail();
    //List<RemoveRedundantUniformMetadatReductionOpportunity> ops =
    //    RemoveRedundantUniformMetadatReductionOpportunities
    //    .findOpportunities(shaderJob,
    //        new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null,
    //            true));
    //assertEquals(1, ops.size());
    //ops.get(0).applyReduction();

    // Check that after applying the reduction opportunity there are no uniforms in the pipeline
    // state and that the shader has not changed.
    CompareAsts.assertEqualAsts(emptyShader, shaderJob.getFragmentShader().get());
    assertEquals(0, shaderJob.getPipelineInfo().getNumUniforms());
  }

  // TODO(478): enable this test once the issue is addressed.
  @Ignore
  @Test
  public void testDoNotRemoveUsed() throws Exception {
    // Make a shader job with a simple fragment shader that declares (but does not use)
    // a uniform.  We don't want the metadata for this uniform to get removed; the declaration
    // of the uniform would have to actually disappear first.
    final String emptyShader = "uniform float unused; void main() { }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("unused", BasicType.FLOAT, Optional.empty(), Collections.singletonList(10.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(emptyShader));
    // Check that initially there is indeed one uniform in the pipeline state.
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be exactly one opportunity to remove a piece of unused pipeline state.
    // TODO(478): remove the Assert.fail(), and un-comment the lines that follow it.
    Assert.fail();
    //List<RemoveRedundantUniformMetadatReductionOpportunity> ops =
    //    RemoveRedundantUniformMetadatReductionOpportunities
    //    .findOpportunities(shaderJob,
    //        new ReducerContext(false, ShadingLanguageVersion.ESSL_100, new RandomWrapper(0), null,
    //            true));
    //assertEquals(0, ops.size());
  }

}
