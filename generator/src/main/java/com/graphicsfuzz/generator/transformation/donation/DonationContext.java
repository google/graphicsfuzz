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

package com.graphicsfuzz.generator.transformation.donation;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DonationContext {

  private final Stmt donorFragment;
  private final Map<String, Type> freeVariables;
  private final List<StructDefinitionType> availableStructs;
  private final FunctionDefinition enclosingFunction;

  DonationContext(Stmt donorFragment, Map<String, Type> freeVariables,
      List<StructDefinitionType> availableStructs,
      FunctionDefinition enclosingFunction) {
    this.donorFragment = donorFragment;
    this.freeVariables = freeVariables;
    this.availableStructs = availableStructs;
    this.enclosingFunction = enclosingFunction;
  }

  Stmt getDonorFragment() {
    return donorFragment;
  }

  Map<String, Type> getFreeVariables() {
    return Collections.unmodifiableMap(freeVariables);
  }

  List<StructDefinitionType> getAvailableStructs() {
    return Collections.unmodifiableList(availableStructs);
  }

  FunctionDefinition getEnclosingFunction() {
    return enclosingFunction;
  }

  Set<String> getDeclaredVariableNames() {
    return new StandardVisitor() {

      private Set<String> names = new HashSet<>();

      @Override
      public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
        names.add(variableDeclInfo.getName());
      }

      Set<String> getNames(Stmt stmt) {
        visit(stmt);
        return Collections.unmodifiableSet(names);
      }

    }.getNames(donorFragment);

  }

  boolean indexesArrayUsingFreeVariable() {

    // Note: we don't use the freeVariables member here, because we want to account for
    // name shadowing.

    return new ScopeTreeBuilder() {

      private boolean found = false;

      private int arrayIndexDepth = 0;

      @Override
      public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
        arrayIndexDepth++;
        super.visitArrayIndexExpr(arrayIndexExpr);
        arrayIndexDepth--;
      }

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (arrayIndexDepth > 0
            && currentScope.lookupScopeEntry(variableIdentifierExpr.getName()) == null) {
          // A free variable that appears under an array index
          found = true;
        }
      }

      public boolean indexesArrayUsingFreeVariable(IAstNode node) {
        visit(node);
        return found;
      }

    }.indexesArrayUsingFreeVariable(donorFragment);

  }

}
