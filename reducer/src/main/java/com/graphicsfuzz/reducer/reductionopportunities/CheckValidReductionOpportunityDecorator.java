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
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class CheckValidReductionOpportunityDecorator implements IReductionOpportunity {

  private final IReductionOpportunity delegate;
  private final ShaderJob shaderJob;
  private final ShadingLanguageVersion shadingLanguageVersion;


  public CheckValidReductionOpportunityDecorator(IReductionOpportunity delegate,
                                                 ShaderJob shaderJob,
                                                 ShadingLanguageVersion shadingLanguageVersion) {
    this.delegate = delegate;
    this.shaderJob = shaderJob;
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  @Override
  public void applyReduction() {
    final ShaderJob before = new GlslShaderJob(
        shaderJob.hasVertexShader()
            ? Optional.of(shaderJob.getVertexShader().cloneAndPatchUp())
            : Optional.empty(),
        shaderJob.hasFragmentShader()
            ? Optional.of(shaderJob.getFragmentShader().cloneAndPatchUp())
            : Optional.empty(),
        new UniformsInfo(shaderJob.getUniformsInfo().toString()));
    delegate.applyReduction();
    final String prefix = "temp_to_validate";
    try {
      Helper.emitShaderJob(
          shaderJob,
          shadingLanguageVersion,
          prefix,
          new File("."),
          null);
      if (shaderJob.hasVertexShader()) {
        checkShaderIsValid(before, prefix, ".vert");
      }
      if (shaderJob.hasFragmentShader()) {
        checkShaderIsValid(before, prefix, ".frag");
      }
    } catch (InterruptedException | IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  private void checkShaderIsValid(ShaderJob before, String prefix, String extension)
      throws IOException, InterruptedException {
    ExecResult execResult = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER,
          new File(prefix + extension));
    if (execResult.res != 0) {
      throw new ReductionLedToInvalidException(before, shaderJob, execResult, delegate);
    }
  }

  @Override
  public VisitationDepth depth() {
    return delegate.depth();
  }

  @Override
  public boolean preconditionHolds() {
    return delegate.preconditionHolds();
  }
}
