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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
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

  /**
   * Prunes uniforms from the shader job, by turning them into global variables initialized to the
   * value the uniform would take, until there are no more than a given number of uniforms in the
   * shader job.
   * @param shaderJob A shader job whose uniforms are to be pruned.
   * @param limit The maximum number of uniforms that should remain.
   * @param prefixesForPriorityPruning A list of prefixes, such that uniforms starting with one of
   *                                   the prefixes should be pruned before other uniforms.
   */
  public static void pruneIfNeeded(ShaderJob shaderJob,
                                   int limit,
                                   List<String> prefixesForPriorityPruning) {

    // Work out how many uniforms need to be pruned to meet the limit.
    final int numToPrune = shaderJob.getPipelineInfo().getNumUniforms() - limit;
    if (numToPrune <= 0) {
      // No pruning is required.
      return;
    }

    // Using the given prefixes, determine those uniforms we would prefer to prune and those
    // uniforms we will only prune if necessary.
    final List<String> pruneFirst = new ArrayList<>();
    final List<String> pruneIfNecessary = new ArrayList<>();
    for (String uniformName : shaderJob.getPipelineInfo().getUniformNames()) {
      if (isPrunable(prefixesForPriorityPruning, uniformName)) {
        pruneFirst.add(uniformName);
      } else {
        pruneIfNecessary.add(uniformName);
      }
    }

    // Order the uniforms so that the ones we prefer to prune come first.
    final List<String> orderedForPruning = new ArrayList<>();
    orderedForPruning.addAll(pruneFirst);
    orderedForPruning.addAll(pruneIfNecessary);

    // Prune as many uniforms as needed.
    for (int i = 0; i < numToPrune; i++) {
      final String uniformName = orderedForPruning.get(i);
      for (TranslationUnit tu : shaderJob.getShaders()) {
        inlineUniform(tu, shaderJob.getPipelineInfo(), uniformName);
      }
      shaderJob.getPipelineInfo().removeUniform(uniformName);
    }

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
      assert arrayInfo.getConstantSize() * baseType.getNumElements() == args.size();
      List<Expr> argExprs = new ArrayList<>();
      for (int index = 0; index < arrayInfo.getConstantSize(); index++) {
        argExprs.add(getBasicTypeLiteralExpr(baseType,
            args.subList(index * baseType.getNumElements(),
                (index + 1) * baseType.getNumElements())));
      }
      return new Initializer(new ArrayConstructorExpr(
          new ArrayType(baseType.getWithoutQualifiers(), arrayInfo.clone()),
          argExprs));
    }
    return new Initializer(getBasicTypeLiteralExpr(baseType, args));
  }

  public static Expr getBasicTypeLiteralExpr(BasicType baseType, List<Number> args) {
    List<Expr> argExprs;
    if (baseType.getElementType() == BasicType.BOOL) {
      argExprs = args.stream().map(item -> new BoolConstantExpr(item.intValue() == 1))
          .collect(Collectors.toList());
    } else if (baseType.getElementType() == BasicType.FLOAT) {
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
