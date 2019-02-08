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

import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.Arrays;
import org.apache.commons.lang3.tuple.Pair;

public class AddDeadOutputWriteMutation extends AddOutputWriteMutation {

  public AddDeadOutputWriteMutation(IInjectionPoint injectionPoint,
                                    IRandom random,
                                    ShadingLanguageVersion shadingLanguageVersion,
                                    GenerationParams generationParams) {
    super(injectionPoint, random, shadingLanguageVersion, generationParams);
  }

  @Override
  Stmt prepareOutputVariableWrite() {
    final Pair<String, Type> outputVariableInfo = chooseOutputVariable();
    final String outputVariableName = outputVariableInfo.getLeft();
    final Type outputVariableType = outputVariableInfo.getRight();

    return AddJumpMutation.makeDeadConditional(injectionPoint,
        new BlockStmt(Arrays.asList(
            new ExprStmt(new BinaryExpr(
                new VariableIdentifierExpr(outputVariableName),
                new Fuzzer(new FuzzingContext(), shadingLanguageVersion, random,
                    generationParams)
                    .fuzzExpr(outputVariableType, false, false, 0), BinOp.ASSIGN)
            )), true),
        random,
        shadingLanguageVersion,
        generationParams);
  }

}
