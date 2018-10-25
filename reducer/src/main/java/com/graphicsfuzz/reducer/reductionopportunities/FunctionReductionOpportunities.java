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

import com.graphicsfuzz.common.ast.AstUtil;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FunctionReductionOpportunities extends StandardVisitor {

  private final Typer typer;

  private final List<FunctionReductionOpportunity> opportunities;

  // Collects the prototypes of all functions that are called at least once
  private final Set<FunctionPrototype> calledFunctions;

  private final List<FunctionPrototype> declaredFunctions; // All functions declared in the shader

  private FunctionReductionOpportunities(TranslationUnit tu, ReducerContext context) {
    this.typer = new Typer(tu, context.getShadingLanguageVersion());
    this.opportunities = new ArrayList<>();
    this.calledFunctions = new HashSet<>();
    this.declaredFunctions = Collections
        .unmodifiableList(AstUtil.getFunctionPrototypesFromShader(tu));
  }

  /**
   * Find all function/struct reduction opportunities for the given translation unit.
   * @param shaderJob The shader job to be searched.
   * @param context Relevant details including the version of GLSL being used
   * @return The opportunities that can be reduced
   */
  static List<FunctionReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<FunctionReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    FunctionReductionOpportunities finder =
        new FunctionReductionOpportunities(tu, context);
    finder.visit(tu);
    finder.getReductionOpportunitiesForDeadFunctions(tu);
    return finder.opportunities;
  }

  private boolean functionIsCalled(FunctionPrototype prototype) {
    return calledFunctions.stream().anyMatch(proto -> proto.matches(prototype));
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    calledFunctions.addAll(findPossibleMatchesForCall(functionCallExpr));
    super.visitFunctionCallExpr(functionCallExpr);
  }

  private Set<FunctionPrototype> findPossibleMatchesForCall(FunctionCallExpr functionCallExpr) {
    Set<FunctionPrototype> candidates = declaredFunctions.stream()
        .filter(proto -> proto.getName().equals(functionCallExpr.getCallee()))
        .filter(proto -> proto.getNumParameters() == functionCallExpr.getNumArgs())
        .collect(Collectors.toSet());
    for (int i = 0; i < functionCallExpr.getNumArgs(); i++) {
      if (!typer.hasType(functionCallExpr.getArg(i))) {
        // If we don't have a type for this argument, we're OK with any function prototype's type
        continue;
      }
      final int currentIndex = i; // Capture i in final variable so it can be used in lambda.
      candidates = candidates.stream().filter(proto ->
          typer.lookupType(functionCallExpr.getArg(currentIndex)).getWithoutQualifiers()
              .equals(proto.getParameters().get(currentIndex).getType().getWithoutQualifiers()))
          .collect(Collectors.toSet());
    }
    return candidates;
  }

  private void getReductionOpportunitiesForDeadFunctions(TranslationUnit tu) {
    for (Declaration decl : tu.getTopLevelDeclarations()) {
      if (decl instanceof FunctionDefinition) {
        FunctionDefinition fd = (FunctionDefinition) decl;
        if (fd.getPrototype().getName().equals("main")) {
          continue;
        }
        if (!functionIsCalled(fd.getPrototype())) {
          opportunities.add(new FunctionReductionOpportunity(tu, fd,
              getVistitationDepth()));
        }
      }
      if (decl instanceof FunctionPrototype) {
        FunctionPrototype fp = (FunctionPrototype) decl;
        if (!functionIsCalled(fp)) {
          opportunities.add(new FunctionReductionOpportunity(tu, fp,
              getVistitationDepth()));
        }
      }
    }
  }

}
