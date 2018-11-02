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
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.inliner.CannotInlineCallException;
import com.graphicsfuzz.common.ast.inliner.Inliner;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IdGenerator;

public class InlineFunctionReductionOpportunity extends AbstractReductionOpportunity {

  private final FunctionCallExpr functionCallExpr;
  private final TranslationUnit tu;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IdGenerator idGenerator;

  public InlineFunctionReductionOpportunity(FunctionCallExpr functionCallExpr, TranslationUnit tu,
        ShadingLanguageVersion shadingLanguageVersion, IdGenerator idGenerator,
        VisitationDepth depth) {
    super(depth);
    this.functionCallExpr = functionCallExpr;
    this.tu = tu;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.idGenerator = idGenerator;
  }

  @Override
  void applyReductionImpl() {
    try {
      Inliner.inline(functionCallExpr, tu, shadingLanguageVersion, idGenerator);
    } catch (CannotInlineCallException exception) {
      // Precondition ensures that this cannot happen.
      assert false;
    }
  }

  @Override
  public boolean preconditionHolds() {
    // We use a node limit of 0 (to say "no limit") because we already checked the limit on
    // creation of this opportunity, and we're OK with the possibility that the limit might now
    // be exceeded due to other transformations.
    return Inliner.canInline(functionCallExpr, tu, shadingLanguageVersion, 0);
  }

}
