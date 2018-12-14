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

package com.graphicsfuzz.generator.transformation.controlflow;

import com.graphicsfuzz.common.ast.TranslationUnit;
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
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.List;

public class AddJumpStmts implements ITransformation {

  public static final String NAME = "jump";

  @Override
  public boolean apply(TranslationUnit tu, TransformationProbabilities probabilities,
        ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
      GenerationParams generationParams) {
    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator, x -> true)
          .getInjectionPoints(
                probabilities::injectJumpAtStmt);
    for (IInjectionPoint injectionPoint : injectionPoints) {
      injectionPoint.inject(prepareJumpStmt(injectionPoint, generator, shadingLanguageVersion,
            generationParams));
    }
    return !injectionPoints.isEmpty();
  }

  @Override
  public String getName() {
    return NAME;
  }

  private IfStmt prepareJumpStmt(IInjectionPoint injectionPoint, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    if (!injectionPoint.inLoop()) {
      return prepareReturnStmt(injectionPoint, generator, shadingLanguageVersion, generationParams);
    }
    // "discard" is only available in fragment shaders.
    final int numJumpStatementTypes =
        generationParams.getShaderKind() == ShaderKind.FRAGMENT ? 4 : 3;
    switch (generator.nextInt(numJumpStatementTypes)) {
      case 0:
        return prepareBreakStmt(injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      case 1:
        return prepareContinueStmt(injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      case 2:
        return prepareReturnStmt(injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      case 3:
        return prepareDiscardStmt(injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      default:
        throw new RuntimeException("Unreachable");
    }
  }

  private IfStmt prepareBreakStmt(IInjectionPoint injectionPoint,
        IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
      GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, BreakStmt.INSTANCE, generator,
        shadingLanguageVersion,
          generationParams);
  }

  private IfStmt prepareContinueStmt(IInjectionPoint injectionPoint,
        IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
      GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, ContinueStmt.INSTANCE, generator,
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
        IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
      GenerationParams generationParams) {
    return makeDeadConditional(injectionPoint, DiscardStmt.INSTANCE, generator,
        shadingLanguageVersion,
          generationParams);
  }

  static IfStmt makeDeadConditional(IInjectionPoint injectionPoint, Stmt thenStmt,
        IRandom generator, ShadingLanguageVersion shadingLanguageVersion,
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
