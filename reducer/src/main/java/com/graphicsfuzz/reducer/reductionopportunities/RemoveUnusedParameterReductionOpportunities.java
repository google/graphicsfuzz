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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;

import com.graphicsfuzz.common.util.ListConcat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>This class finds opportunities to remove an argument of a function if the function
 * does not use that argument.</p>
 *
 * <p>It does not try to remove an argument of a function if that function is overloaded.
 * This is to avoid the problem of creating an overload collision.  We could be less
 * conservative about this.</p>
 *
 *
 */
public class RemoveUnusedParameterReductionOpportunities
    extends ReductionOpportunitiesBase<RemoveUnusedParameterReductionOpportunity> {

  static List<RemoveUnusedParameterReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<RemoveUnusedParameterReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    RemoveUnusedParameterReductionOpportunities finder =
        new RemoveUnusedParameterReductionOpportunities(tu, context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  /**
   * Maps each function definition to the set of parameters that it does not reference.
   */
  private Map<FunctionDefinition, List<ParameterDecl>> functionsToUnusedParameters;

  /**
   * Tracks which parameters a function references as the function is being traversed.
   * Null between function traversals.
   */
  private List<ParameterDecl> unusedParametersForCurrentFunction;

  private RemoveUnusedParameterReductionOpportunities(TranslationUnit tu,
                                                      ReducerContext context) {
    super(tu, context);
    this.functionsToUnusedParameters = new HashMap<>();
    this.unusedParametersForCurrentFunction = null;
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {

    if (!context.reduceEverywhere()) {
      // If we are not reducing everywhere, do not consider these reduction opportunities at all.
      // The rationale for this is that reducing a parameter can change semantics if an actual
      // parameter expression is side-effecting.
      return;
    }

    super.visitTranslationUnit(translationUnit);
    Map<String, Long> functionNameToCount = functionsToUnusedParameters.keySet()
        .stream()
        .map(item -> item.getPrototype().getName())
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    for (FunctionDefinition funDef : functionsToUnusedParameters.keySet()) {
      if (functionNameToCount.get(funDef.getPrototype().getName()) > 1) {
        // The function is overloaded, so do not consider removing its unused parameters.
        continue;
      }
      for (ParameterDecl parameterDecl : functionsToUnusedParameters.get(funDef)) {
        addOpportunity(new RemoveUnusedParameterReductionOpportunity(translationUnit,
            funDef,
            parameterDecl,
            getVistitationDepth()));
      }
    }

  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert unusedParametersForCurrentFunction == null;
    unusedParametersForCurrentFunction = new ArrayList<>();
    unusedParametersForCurrentFunction.addAll(functionDefinition.getPrototype().getParameters());
    super.visitFunctionDefinition(functionDefinition);
    functionsToUnusedParameters.put(functionDefinition, unusedParametersForCurrentFunction);
    unusedParametersForCurrentFunction = null;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final ScopeEntry scopeEntry = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
    if (scopeEntry != null && scopeEntry.hasParameterDecl()) {
      unusedParametersForCurrentFunction.remove(scopeEntry.getParameterDecl());
    }
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    super.visitFunctionPrototype(functionPrototype);
  }

}
