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

package com.graphicsfuzz.reducer;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Base class for checking whether a function, callable indirectly from main,
 * has a feature of interest.  In a sublass you should override a suitable visitor
 * method to check for the feature of interest, and then invoke the trigger function
 * when the feature is found.
 */
public abstract class CheckAstFeatureVisitor extends ScopeTreeBuilder {

  private Optional<FunctionDefinition> triggerFunction = Optional.empty();
  private Map<String, Set<String>> callsIndirectly = new HashMap<String, Set<String>>();

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    final Set<String> callSelf = new HashSet<>();
    callSelf.add(functionPrototype.getName());
    callsIndirectly.put(functionPrototype.getName(), callSelf);
    super.visitFunctionPrototype(functionPrototype);
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);
    final String enclosingFunctionName = enclosingFunction.getPrototype().getName();
    final String calledFunctionName = functionCallExpr.getCallee();
    assert callsIndirectly.containsKey(enclosingFunctionName);
    if (!callsIndirectly.containsKey(calledFunctionName)) {
      return;
    }
    for (String function : callsIndirectly.keySet()) {
      if (callsIndirectly.get(function).contains(enclosingFunctionName)) {
        callsIndirectly.get(function).addAll(callsIndirectly
              .get(calledFunctionName));
      }
    }
  }

  boolean check(TranslationUnit tu) {
    visit(tu);
    return triggerFunction.isPresent()
          && callsIndirectly.get("main").contains(triggerFunction.get().getPrototype().getName());
  }

  /**
   * Use this method to register that the feature of interest has been found.
   */
  public void trigger() {
    this.triggerFunction = Optional.of(enclosingFunction);
  }
}
