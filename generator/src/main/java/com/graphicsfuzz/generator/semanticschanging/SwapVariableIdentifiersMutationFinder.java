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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.util.IRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Finds mutations such as: v = x + y -> v = a + b.
 */
public class SwapVariableIdentifiersMutationFinder extends Expr2ExprMutationFinder {

  private final IRandom generator;

  public SwapVariableIdentifiersMutationFinder(TranslationUnit tu,
                                               IRandom generator) {
    super(tu);
    this.generator = generator;
  }

  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);

    if (underForLoopHeader) {
      return;
    }

    if (currentScope.lookupType(variableIdentifierExpr.getName()) == null) {
      return;
    }

    assert currentScope.lookupType(variableIdentifierExpr.getName()).getWithoutQualifiers() != null;

    final List<String> candidateVariables = getCandidateVariables(variableIdentifierExpr.getName());

    if (candidateVariables.isEmpty()) {
      return;
    }

    addMutation(new Expr2ExprMutation(
        parentMap.getParent(variableIdentifierExpr),
        variableIdentifierExpr,
        new VariableIdentifierExpr(
            candidateVariables.get(generator.nextInt(candidateVariables.size())))));
  }

  private List<String> getCandidateVariables(String varIdentifier) {
    return currentScope.namesOfAllVariablesInScope().stream()
        .filter(item -> !item.equals(varIdentifier)
            && currentScope.lookupType(item) != null
            && currentScope.lookupType(varIdentifier).getWithoutQualifiers().equals(
            currentScope.lookupType(item).getWithoutQualifiers()))
        .collect(Collectors.toList());
  }

}
