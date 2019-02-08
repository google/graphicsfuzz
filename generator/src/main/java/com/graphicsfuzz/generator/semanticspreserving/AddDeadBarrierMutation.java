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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.ArrayList;

public class AddDeadBarrierMutation implements Mutation {

  private final IInjectionPoint injectionPoint;
  private final IRandom random;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final GenerationParams generationParams;

  public AddDeadBarrierMutation(IInjectionPoint injectionPoint,
                                IRandom random,
                                ShadingLanguageVersion shadingLanguageVersion,
                                GenerationParams generationParams) {
    this.injectionPoint = injectionPoint;
    this.random = random;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.generationParams = generationParams;
  }

  @Override
  public void apply() {
    injectionPoint.inject(prepareDeadBarrier(injectionPoint, random, shadingLanguageVersion,
        generationParams));

  }

  private Stmt prepareDeadBarrier(IInjectionPoint injectionPoint,
                                  IRandom generator,
                                  ShadingLanguageVersion shadingLanguageVersion,
                                  GenerationParams generationParams) {
    return AddJumpMutation.makeDeadConditional(injectionPoint,
        new ExprStmt(new FunctionCallExpr("barrier", new ArrayList<>())),
        generator,
        shadingLanguageVersion,
        generationParams);
  }

}
