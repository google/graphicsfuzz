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
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class CheckValidReductionOpportunityDecorator implements IReductionOpportunity {

  private final IReductionOpportunity delegate;
  private final TranslationUnit tu;
  private final ShadingLanguageVersion shadingLanguageVersion;


  public CheckValidReductionOpportunityDecorator(IReductionOpportunity delegate,
        TranslationUnit tu, ShadingLanguageVersion shadingLanguageVersion) {
    this.delegate = delegate;
    this.tu = tu;
    this.shadingLanguageVersion = shadingLanguageVersion;
  }

  @Override
  public void applyReduction() {
    final TranslationUnit tuBefore = tu.cloneAndPatchUp();
    delegate.applyReduction();
    final String filename = "temp_to_validate.frag";
    try {
      Helper.emitShader(shadingLanguageVersion, ShaderKind.FRAGMENT, tu, Optional.empty(),
          new File(filename));
      ExecResult execResult = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER,
            new File(filename));
      if (execResult.res != 0) {
        throw new ReductionLedToInvalidException(tuBefore, tu, execResult, delegate);
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
