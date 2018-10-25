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

import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.io.IOException;

public class CheckValidReductionOpportunityDecorator implements IReductionOpportunity {

  private final IReductionOpportunity delegate;
  private final ShaderJob shaderJob;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final ShaderJobFileOperations fileOps;


  public CheckValidReductionOpportunityDecorator(IReductionOpportunity delegate,
                                                 ShaderJob shaderJob,
                                                 ShadingLanguageVersion shadingLanguageVersion,
                                                 ShaderJobFileOperations fileOps) {
    this.delegate = delegate;
    this.shaderJob = shaderJob;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.fileOps = fileOps;
  }

  @Override
  public void applyReduction() {
    final ShaderJob before = shaderJob.clone();
    delegate.applyReduction();
    final File outputShaderJobFile = new File("temp_to_validate.json");
    try {
      fileOps.writeShaderJobFile(
          shaderJob,
          outputShaderJobFile
      );

      boolean valid = fileOps.areShadersValid(outputShaderJobFile, false);
      if (!valid) {
        throw new ReductionLedToInvalidException(before, shaderJob, delegate);
      }

    } catch (InterruptedException | IOException exception) {
      throw new RuntimeException(exception);
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
