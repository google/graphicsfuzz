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

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

public class RemoveRedundantUniformMetadataReductionOpportunitiesTest {

  @Test
  public void testRemoveUnused() throws Exception {
    // Make a shader job with an empty fragment shader, and declare one uniform in the pipeline
    // state such that the uniform is not used.
    final String emptyShader = "void main() { }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("unused", BasicType.FLOAT, Optional.empty(),
        Collections.singletonList(10.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(emptyShader));
    // Check that initially there is indeed one uniform in the pipeline state.
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be exactly one opportunity to remove a piece of unused pipeline state.
    List<RemoveRedundantUniformMetadataReductionOpportunity> ops =
        RemoveRedundantUniformMetadataReductionOpportunities
        .findOpportunities(shaderJob,
            new ReducerContext(false, true,
                ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();

    // Check that after applying the reduction opportunity there are no uniforms in the pipeline
    // state and that the shader has not changed.
    CompareAsts.assertEqualAsts(emptyShader, shaderJob.getFragmentShader().get());
    assertEquals(0, shaderJob.getPipelineInfo().getNumUniforms());
  }

  @Test
  public void testRemoveUnusedNameShadowing() throws Exception {
    // Checks for the case where a uniform declared in the pipeline state is not used, but another
    // variable shadows its name.
    final String minimalShader = "void main() { int shadow; }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("shadow", BasicType.FLOAT, Optional.empty(),
        Collections.singletonList(10.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(minimalShader));
    // The pipeline info has an unused uniform named 'shadow', and the shader declares an unrelated
    // variable called 'shadow'.  We would like the uniform to be removed from the pipeline info,
    // as it is not used.
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be exactly one opportunity to remove a piece of unused pipeline state.
    List<RemoveRedundantUniformMetadataReductionOpportunity> ops =
        RemoveRedundantUniformMetadataReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, true, ShadingLanguageVersion.ESSL_100,
                    new RandomWrapper(0),
                new IdGenerator()));
    assertEquals(1, ops.size());
    ops.get(0).applyReduction();

    // Check that after applying the reduction opportunity there are no uniforms in the pipeline
    // state and that the shader has not changed.
    CompareAsts.assertEqualAsts(minimalShader, shaderJob.getFragmentShader().get());
    assertEquals(0, shaderJob.getPipelineInfo().getNumUniforms());
  }

  @Test
  public void testDoNotRemoveUsed() throws Exception {
    // Make a shader job with a simple fragment shader that declares (but does not use)
    // a uniform.  We don't want the metadata for this uniform to get removed; the declaration
    // of the uniform would have to actually disappear first.
    final String emptyShader = "uniform float unused; void main() { }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("unused", BasicType.FLOAT, Optional.empty(),
        Collections.singletonList(10.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(emptyShader));
    // Check that initially there is indeed one uniform in the pipeline state.
    assertEquals(1, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be no opportunities to remove a piece of unused pipeline state.
    List<RemoveRedundantUniformMetadataReductionOpportunity> ops =
        RemoveRedundantUniformMetadataReductionOpportunities
        .findOpportunities(shaderJob,
            new ReducerContext(false, true,
                ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));
    assertEquals(0, ops.size());
  }

  @Test
  public void testDoNotRemoveUsedMultipleShaders() throws Exception {
    // A shader job with a vertex shader and fragment shader that each use a different
    // uniform (but that do not use a uniform in common).  Neither uniform should be removed
    // from the pipeline state.
    final String minimalVertexShader = "uniform float used_in_vertex_only; void main() { "
        + "used_in_vertex_only; }";
    final String minimalFragmentShader = "uniform float used_in_fragment_only; void main() { "
        + "used_in_fragment_only; }";
    final PipelineInfo pipelineInfo = new PipelineInfo();
    pipelineInfo.addUniform("used_in_vertex_only", BasicType.FLOAT, Optional.empty(),
        Collections.singletonList(10.0));
    pipelineInfo.addUniform("used_in_fragment_only", BasicType.FLOAT, Optional.empty(),
        Collections.singletonList(20.0));
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(),
        pipelineInfo, ParseHelper.parse(minimalVertexShader, ShaderKind.VERTEX),
        ParseHelper.parse(minimalFragmentShader, ShaderKind.FRAGMENT));
    // Check that initially there are indeed two uniforms in the pipeline state.
    assertEquals(2, shaderJob.getPipelineInfo().getNumUniforms());

    // There should be no opportunities to remove a piece of unused pipeline state, since both
    // uniforms are referenced.
    List<RemoveRedundantUniformMetadataReductionOpportunity> ops =
        RemoveRedundantUniformMetadataReductionOpportunities
            .findOpportunities(shaderJob,
                new ReducerContext(false, true,
                    ShadingLanguageVersion.ESSL_100, new RandomWrapper(0),
                new IdGenerator()));
    assertEquals(0, ops.size());
  }

}
