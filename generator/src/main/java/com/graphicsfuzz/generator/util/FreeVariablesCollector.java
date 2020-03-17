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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.util.OpenGlConstants;
import java.util.HashMap;
import java.util.Map;


public class FreeVariablesCollector extends ScopeTrackingVisitor {

  // A variable v is *used freely* in an AST subtree rooted at node n if:
  // - v is not a global variable
  // - v's declaration is outside the subtree rooted at n.
  //
  // Example:
  //
  // int x;
  // float y;
  //
  // void foo(int p, float q) {
  //   int x;
  //   { <----------------------- AST node n
  //     int a;
  //     float b;
  //     a = p + x + int(b); <--- p and x are used freely (x because the reference refers to the
  //                              declaration of x in foo)
  //     int x;
  //     x = float(y) + q; <----- q is used freely; x is not because of the declaration on the
  //                              previous line; y is not because it is a global
  //   }
  // }

  // This is the donor fragment for which we would like to compute free variables.
  private final Stmt donorFragment;

  // This is the scope of variables available at the start of the donor fragment.
  private Scope scopeAtStartOfDonorFragment;

  // The result of the collection.
  private final Map<String, Type> freeVariables;

  public FreeVariablesCollector(TranslationUnit donor, Stmt donorFragment) {
    this.donorFragment = donorFragment;
    this.scopeAtStartOfDonorFragment = null;
    this.freeVariables = new HashMap<>();
    visit(donor);
  }

  public Map<String, Type> getFreeVariables() {
    return freeVariables;
  }

  @Override
  public void visit(IAstNode node) {
    if (node == donorFragment) {

      // We have reached the donor fragment.
      //
      // We figure out which global variables are in scope at this point (which involves excluding
      // those globals that are shadowed), and process the donor subtree in the context of only
      // those globals.  References to other variables are exactly the free variables.

      assert scopeAtStartOfDonorFragment == null;

      // Walk up the scope tree until global scope is reached.
      Scope tempScope = getCurrentScope();
      while (tempScope.hasParent()) {
        tempScope = tempScope.getParent();
      }
      assert tempScope != getCurrentScope() : "The donor fragment should not be at global scope.";

      // At this point, 'tempScope' is global scope.
      //
      // We create a version of global scope that excludes all names that are shadowed by more
      // local declarations.
      final Scope globalScopeWithoutShadows = tempScope.shallowClone();
      for (String name : tempScope.namesOfAllVariablesInScope()) {
        if (tempScope.lookupScopeEntry(name) != getCurrentScope().lookupScopeEntry(name)) {
          // 'name' is shadowed, because we get a different scope entry when we look it up at global
          // scope vs. at the current scope.
          globalScopeWithoutShadows.remove(name);
        }
      }

      // We process the donor fragment in the context of the global, non-shadowed variables.  Any
      // declarations that we cannot find in this scope are free variables, and
      // 'scopeAtStartOfDonorFragmenet' will yield their types.
      scopeAtStartOfDonorFragment = swapCurrentScope(new Scope(globalScopeWithoutShadows));
    }
    super.visit(node);
    if (node == donorFragment) {
      // We are done with the donor fragment, so restore the original scope.
      assert scopeAtStartOfDonorFragment != null;
      swapCurrentScope(scopeAtStartOfDonorFragment);
      scopeAtStartOfDonorFragment = null;
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    String name = variableIdentifierExpr.getName();
    if (isBuiltinVariable(name)) {
      return;
    }
    if (scopeAtStartOfDonorFragment != null && getCurrentScope().lookupType(name) == null) {
      Type type = scopeAtStartOfDonorFragment.lookupType(name);
      if (type == null) {
        throw new RuntimeException(
            "Found variable '" + name + "' that is not typed in the current scope.");
      }
      // Clone the type so that we can do what we want with it when we go on to process the free
      // variables.
      freeVariables.put(name, type.clone());
    }
  }

  private boolean isBuiltinVariable(String name) {
    switch (name) {
      case OpenGlConstants.GL_FRAG_COORD:
      case OpenGlConstants.GL_FRAG_COLOR:
        return true;
      default:
        return false;
    }
  }

}
