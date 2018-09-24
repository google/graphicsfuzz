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
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Strips functions in a shader that are not called directly or indirectly from main.
 */
public class StripUnusedFunctions extends StandardVisitor {

  private Optional<FunctionDefinition> enclosingFunction;
  private Map<String, Set<String>> callGraphEdges;

  private StripUnusedFunctions() {
    this.enclosingFunction = Optional.empty();
    this.callGraphEdges = new HashMap<>();
  }

  public static void strip(TranslationUnit tu) {
    new StripUnusedFunctions().applyStrip(tu);
  }

  public void applyStrip(TranslationUnit tu) {
    visit(tu);
    Set<String> callableFromMain = computeCallable("main", new HashSet<>());
    sweep(tu, callableFromMain);
  }

  private void sweep(TranslationUnit tu, Set<String> callableFromMain) {
    for (int i = tu.getTopLevelDeclarations().size() - 1; i >= 0; i--) {
      Declaration decl = tu.getTopLevelDeclarations().get(i);
      if (decl instanceof FunctionPrototype) {
        if (!callableFromMain.contains(((FunctionPrototype) decl).getName())) {
          tu.removeTopLevelDeclaration(i);
        }
      }
      if (decl instanceof FunctionDefinition) {
        if (!callableFromMain.contains(((FunctionDefinition) decl).getPrototype().getName())) {
          tu.removeTopLevelDeclaration(i);
        }
      }
    }
  }

  private Set<String> computeCallable(String functionName, Set<String> previouslySeen) {
    if (previouslySeen.contains(functionName)) {
      return new HashSet<>();
    }
    Set<String> result = new HashSet<>();
    result.add(functionName);
    previouslySeen.add(functionName);
    if (callGraphEdges.containsKey(functionName)) {
      for (String callee : callGraphEdges.get(functionName)) {
        result.addAll(computeCallable(callee, previouslySeen));
      }
    }
    return result;
  }


  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert !enclosingFunction.isPresent();
    enclosingFunction = Optional.of(functionDefinition);
    super.visitFunctionDefinition(functionDefinition);
    enclosingFunction = Optional.empty();
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);
    if (enclosingFunction.isPresent()) {
      addCallGraphEdge(enclosingFunction.get().getPrototype().getName(),
          functionCallExpr.getCallee());
    }
  }

  private void addCallGraphEdge(String caller, String callee) {
    if (!callGraphEdges.containsKey(caller)) {
      callGraphEdges.put(caller, new HashSet<>());
    }
    callGraphEdges.get(caller).add(callee);
  }

}
