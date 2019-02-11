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

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class AddLiveOutputWriteMutation extends AddOutputWriteMutation {

  AddLiveOutputWriteMutation(IInjectionPoint injectionPoint,
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

    List<Stmt> stmts = new ArrayList<>();
    final String backupName = Constants.GLF_OUT_VAR_BACKUP_PREFIX + outputVariableName;
    stmts.add(new DeclarationStmt(new VariablesDeclaration(
        outputVariableType, new VariableDeclInfo(backupName,
        null, null))));
    stmts.add(new ExprStmt(new BinaryExpr(new VariableIdentifierExpr(backupName),
        new VariableIdentifierExpr(outputVariableName), BinOp.ASSIGN)));
    final Fuzzer fuzzer = new Fuzzer(new FuzzingContext(), shadingLanguageVersion, random,
        generationParams);
    stmts.add(new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr(outputVariableName),
        fuzzer
            .fuzzExpr(outputVariableType, false, false, 0), BinOp.ASSIGN)));

    OpaqueExpressionGenerator opaqueExpressionGenerator = new OpaqueExpressionGenerator(random,
        generationParams, shadingLanguageVersion);

    stmts.add(new IfStmt(AddWrappingConditionalMutation.makeWrappedIfCondition(
        opaqueExpressionGenerator.makeOpaqueBoolean(true,
            BasicType.BOOL, false, 0, fuzzer), true),
        new BlockStmt(
            Arrays.asList(new ExprStmt(new BinaryExpr(
                new VariableIdentifierExpr(outputVariableName),
                new VariableIdentifierExpr(backupName), BinOp.ASSIGN))),
            true), null));

    return new BlockStmt(stmts, true);
  }

}
