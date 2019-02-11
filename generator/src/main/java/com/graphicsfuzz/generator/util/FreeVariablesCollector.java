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
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.OpenGlConstants;
import java.util.HashMap;
import java.util.Map;

public class FreeVariablesCollector extends ScopeTreeBuilder {

  private final Stmt donorFragment;
  private Scope enclosingScope;
  private final Map<String, Type> freeVariables;

  public FreeVariablesCollector(TranslationUnit donor, Stmt donorFragment) {
    this.donorFragment = donorFragment;
    this.enclosingScope = null;
    this.freeVariables = new HashMap<>();
    visit(donor);
  }

  public Map<String, Type> getFreeVariables() {
    return freeVariables;
  }

  @Override
  public void visit(IAstNode node) {
    if (node == donorFragment) {
      enclosingScope = currentScope;
      currentScope = new Scope(null);
      super.visit(node);
      // early exit now, if we implement that
      currentScope = enclosingScope;
      enclosingScope = null;
    } else {
      super.visit(node);
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    String name = variableIdentifierExpr.getName();
    if (isBuiltinVariable(name)) {
      return;
    }
    if (enclosingScope != null && currentScope.lookupType(name) == null) {
      Type type = enclosingScope.lookupType(name);
      if (type == null) {
        throw new RuntimeException(
            "Found variable '" + name + "' that is not typed in current not enclosing scope.");
      }
      //noinspection ConstantConditions
      assert type != null;
      freeVariables.put(name, type);
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
