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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.Map;

public class ApplySubstitution extends ScopeTreeBuilder {

  private Map<String, String> substitution;

  public ApplySubstitution(Map<String, String> substitution, IAstNode node) {
    this.substitution = substitution;
    visit(node);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    String name = variableIdentifierExpr.getName();
    if (currentScope.lookupType(name) == null) {
      if (substitution.containsKey(name)) {
        variableIdentifierExpr.setName(substitution.get(name));
      }
    }
  }

}
