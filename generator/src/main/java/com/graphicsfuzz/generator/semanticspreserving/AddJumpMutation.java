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

import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.ArrayList;

public class AddJumpMutation implements Mutation {

  private final IInjectionPoint injectionPoint;
  private final IRandom random;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final GenerationParams generationParams;

  public AddJumpMutation(IInjectionPoint injectionPoint,
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
    injectionPoint.inject(prepareJumpStmt());
  }

  private IfStmt prepareJumpStmt() {
    if (!injectionPoint.inLoop()) {
      return prepareReturnStmt(injectionPoint, random, shadingLanguageVersion, generationParams);
    }
    // "discard" is only available in fragment shaders.
    final int numJumpStatementTypes =
        generationParams.getShaderKind() == ShaderKind.FRAGMENT ? 4 : 3;
    switch (random.nextInt(numJumpStatementTypes)) {
      case 0:
        return prepareBreakStmt(injectionPoint, random, shadingLanguageVersion,
            generationParams);
      case 1:
        return prepareContinueStmt(injectionPoint, random, shadingLanguageVersion,
            generationParams);
      case 2:
        return prepareReturnStmt(injectionPoint, random, shadingLanguageVersion,
            generationParams);
      case 3:
        return prepareDiscardStmt(injectionPoint, random, shadingLanguageVersion,
            generationParams);
      default:
        throw new RuntimeException("Unreachable");
    }
  }

  private IfStmt prepareBreakStmt(IInjectionPoint injectionPoint,
                                  IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
                                  GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, new BreakStmt(), generator,
        shadingLanguageVersion,
        generationParams);
  }

  private IfStmt prepareContinueStmt(IInjectionPoint injectionPoint,
                                     IRandom generator,
                                     ShadingLanguageVersion shadingLanguageVersion,
                                     GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, new ContinueStmt(), generator,
        shadingLanguageVersion,
        generationParams);
  }

  private IfStmt prepareReturnStmt(IInjectionPoint injectionPoint,
                                   IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
                                   GenerationParams generationParams) {
    Type returnType = injectionPoint.getEnclosingFunction().getPrototype().getReturnType();
    Stmt stmtToInject;
    if (returnType.hasCanonicalConstant()) {
      stmtToInject = new ReturnStmt(returnType.getCanonicalConstant());
    } else if (returnType.getWithoutQualifiers() == VoidType.VOID) {
      stmtToInject = new ReturnStmt();
    } else {
      stmtToInject = new BlockStmt(new ArrayList<>(), true);
    }
    return makeDeadConditional(injectionPoint, stmtToInject, generator, shadingLanguageVersion,
        generationParams);
  }

  private IfStmt prepareDiscardStmt(IInjectionPoint injectionPoint,
                                    IRandom generator,
                                    ShadingLanguageVersion shadingLanguageVersion,
                                    GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, new DiscardStmt(), generator,
        shadingLanguageVersion,
        generationParams);
  }

  public static IfStmt makeDeadConditional(IInjectionPoint injectionPoint,
                                           Stmt thenStmt,
                                           IRandom generator,
                                           ShadingLanguageVersion shadingLanguageVersion,
                                           GenerationParams generationParams) {
    return new IfStmt(
        new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
            .makeDeadCondition(
                new Fuzzer(new FuzzingContext(injectionPoint.scopeAtInjectionPoint()),
                    shadingLanguageVersion, generator,
                    generationParams)),
        thenStmt, null);
  }

}
