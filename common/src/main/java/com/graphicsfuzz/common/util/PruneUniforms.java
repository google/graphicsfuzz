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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class PruneUniforms {

  private PruneUniforms() {
    // Utility class
  }

  public static boolean prune(ShaderJob shaderJob,
                              int limit, List<String> prunablePrefixes) {
    final int numToPrune = shaderJob.getPipelineInfo().getNumUniforms() - limit;
    if (numToPrune < 0) {
      return true;
    }
    final List<String> candidatesForPruning = new ArrayList<>();
    candidatesForPruning.addAll(shaderJob
        .getPipelineInfo()
        .getUniformNames()
        .stream()
        .filter(item -> isPrunable(prunablePrefixes, item))
        .collect(Collectors.toList()));

    // Sort in order to ensure determinism.
    candidatesForPruning.sort(String::compareTo);

    if (candidatesForPruning.size() < numToPrune) {
      // Too few uniforms meet the criteria for pruning.
      return false;
    }

    for (String uniformName : candidatesForPruning.subList(0, numToPrune)) {
      for (TranslationUnit tu : shaderJob.getShaders()) {
        inlineUniform(tu, shaderJob.getPipelineInfo(), uniformName);
      }
      shaderJob.getPipelineInfo().removeUniform(uniformName);
    }

    return true;
  }

  private static void inlineUniform(TranslationUnit tu, PipelineInfo pipelineInfo,
                                    String uniformName) {
    boolean found = false; // For sanity-checking

    final List<Declaration> newTopLevelDeclarations = new ArrayList<>();
    for (Declaration decl : tu.getTopLevelDeclarations()) {
      if (decl instanceof VariablesDeclaration
          && ((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
        final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
        for (int i = 0; i < variablesDeclaration.getNumDecls(); i++) {
          final VariableDeclInfo declInfo = variablesDeclaration.getDeclInfo(i);
          if (!declInfo.getName().equals(uniformName)) {
            continue;
          }
          assert !found;
          found = true;
          assert !declInfo.hasInitializer();
          final BasicType withoutQualifiers = (BasicType) variablesDeclaration.getBaseType()
              .getWithoutQualifiers();
          declInfo.setInitializer(makeInitializer(
              withoutQualifiers,
              declInfo.getArrayInfo(), pipelineInfo.getArgs(uniformName)));
          final VariablesDeclaration newVariablesDeclaration = new VariablesDeclaration(
              withoutQualifiers, declInfo);
          variablesDeclaration.removeDeclInfo(i);

          newTopLevelDeclarations.add(newVariablesDeclaration);
          break;
        }
        if (variablesDeclaration.getNumDecls() > 0) {
          // Keep the original variables declaration as it still has other uniforms.
          newTopLevelDeclarations.add(variablesDeclaration);
        }
      } else {
        newTopLevelDeclarations.add(decl);
      }
    }
    tu.setTopLevelDeclarations(newTopLevelDeclarations);
  }

  private static boolean isPrunable(List<String> prunablePrefixes, String name) {
    return prunablePrefixes.stream()
        .anyMatch(name::startsWith);
  }

  private static Initializer makeInitializer(BasicType baseType,
                                             ArrayInfo arrayInfo,
                                             List<Number> args) {
    if (arrayInfo != null) {
      assert arrayInfo.getSize() * baseType.getNumElements() == args.size();
      List<Expr> argExprs = new ArrayList<>();
      for (int index = 0; index < arrayInfo.getSize(); index++) {
        argExprs.add(getBasicTypeLiteralExpr(baseType,
            args.subList(index * baseType.getNumElements(),
                (index + 1) * baseType.getNumElements())));
      }
      return new ScalarInitializer(new ArrayConstructorExpr(
          new ArrayType(baseType.getWithoutQualifiers(), arrayInfo.clone()),
          argExprs));
    }
    return new ScalarInitializer(getBasicTypeLiteralExpr(baseType, args));
  }

  public static Expr getBasicTypeLiteralExpr(BasicType baseType, List<Number> args) {
    List<Expr> argExprs;
    if (baseType.getElementType() == BasicType.FLOAT) {
      argExprs = args.stream().map(item -> new FloatConstantExpr(item.toString()))
          .collect(Collectors.toList());
    } else if (baseType.getElementType() == BasicType.UINT) {
      argExprs = args.stream().map(item -> new UIntConstantExpr(item.toString() + "u"))
          .collect(Collectors.toList());
    } else {
      argExprs = args.stream().map(item -> new IntConstantExpr(item.toString()))
          .collect(Collectors.toList());
    }
    if (argExprs.size() == 1) {
      return argExprs.get(0);
    }
    return new TypeConstructorExpr(baseType.toString(),
        argExprs);
  }

}
