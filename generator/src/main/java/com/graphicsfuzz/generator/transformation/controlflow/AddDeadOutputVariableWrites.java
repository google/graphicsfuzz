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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class AddDeadOutputVariableWrites extends AddOutputVariableWrites {

  public static final String NAME = "add_dead_output_variable_writes";

  @Override
  public void apply(TranslationUnit tu, TransformationProbabilities probabilities,
        ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
      GenerationParams generationParams) {
    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator, x -> true)
          .getInjectionPoints(
                probabilities::addDeadFragColorWrites);
    for (IInjectionPoint injectionPoint : injectionPoints) {
      if (hasAvailableOutVars(injectionPoint, shadingLanguageVersion, generationParams)) {
        injectionPoint.inject(prepareFragColorWrite(injectionPoint, generator,
            shadingLanguageVersion,
            generationParams));
      }
    }
  }

  private IfStmt prepareFragColorWrite(IInjectionPoint injectionPoint, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    final Pair<String, Type> outputVariableInfo = chooseOutputVariable(injectionPoint, generator,
        shadingLanguageVersion, generationParams);
    final String outputVariableName = outputVariableInfo.getLeft();
    final Type outputVariableType = outputVariableInfo.getRight();

    return AddJumpStmts.makeDeadConditional(injectionPoint,
          new BlockStmt(Arrays.asList(
                new ExprStmt(new BinaryExpr(
                      new VariableIdentifierExpr(outputVariableName),
                      new Fuzzer(new FuzzingContext(), shadingLanguageVersion, generator,
                          generationParams)
                            .fuzzExpr(outputVariableType, false, false, 0), BinOp.ASSIGN)
                )), true),
          generator,
        shadingLanguageVersion,
          generationParams);
  }

  @Override
  public String getName() {
    return NAME;
  }
}
