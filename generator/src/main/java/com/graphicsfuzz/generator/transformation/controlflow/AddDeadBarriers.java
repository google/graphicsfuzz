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

package com.graphicsfuzz.generator.transformation.controlflow;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.List;

public class AddDeadBarriers implements ITransformation {

  public static final String NAME = "dead_barriers";

  @Override
  public boolean apply(TranslationUnit tu, TransformationProbabilities probabilities,
                       ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
                       GenerationParams generationParams) {
    // Barriers are only relevant for compute shaders.
    assert tu.getShaderKind() == ShaderKind.COMPUTE;
    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator, x -> true)
        .getInjectionPoints(
            probabilities::injectDeadBarrierAtStmt);
    for (IInjectionPoint injectionPoint : injectionPoints) {
      injectionPoint.inject(prepareDeadBarrier(injectionPoint, generator, shadingLanguageVersion,
          generationParams));
    }
    return !injectionPoints.isEmpty();
  }

  private Stmt prepareDeadBarrier(IInjectionPoint injectionPoint,
                                  IRandom generator,
                                  ShadingLanguageVersion shadingLanguageVersion,
                                  GenerationParams generationParams) {
    return AddJumpStmts.makeDeadConditional(injectionPoint,
        new ExprStmt(new FunctionCallExpr("barrier", new ArrayList<>())),
        generator,
        shadingLanguageVersion,
        generationParams);
  }

  @Override
  public String getName() {
    return NAME;
  }
}
