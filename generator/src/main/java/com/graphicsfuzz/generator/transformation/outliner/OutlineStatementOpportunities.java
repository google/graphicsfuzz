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

package com.graphicsfuzz.generator.transformation.outliner;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class OutlineStatementOpportunities extends ScopeTreeBuilder {

  private final List<OutlineStatementOpportunity> opportunities;
  private final TranslationUnit tu;

  public OutlineStatementOpportunities(TranslationUnit tu) {
    this.opportunities = new ArrayList<>();
    this.tu = tu;
    visit(tu);
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    super.visitExprStmt(exprStmt);
    if (!OutlineStatementOpportunity.isAssignment(exprStmt)) {
      return;
    }
    BinaryExpr be = (BinaryExpr) exprStmt.getExpr();
    if (!OutlineStatementOpportunity.assignsDirectlyToVariable(be)) {
      return;
    }
    if (referencesArray(be.getRhs(), currentScope)) {
      return;
    }
    opportunities.add(new OutlineStatementOpportunity(exprStmt, currentScope, tu,
          enclosingFunction));
  }

  private boolean referencesArray(Expr expr, Scope enclosingScope) {
    return new CheckPredicateVisitor() {
      private Scope enclosingScope;

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        Type type = enclosingScope.lookupType(variableIdentifierExpr.getName());
        if (type != null && type.getWithoutQualifiers() instanceof ArrayType) {
          predicateHolds();
        }
      }

      public boolean referencesArray(Expr expr, Scope enclosingScope) {
        this.enclosingScope = enclosingScope;
        return test(expr);
      }
    }.referencesArray(expr, enclosingScope);
  }

  List<OutlineStatementOpportunity> getAllOpportunities() {
    return Collections.unmodifiableList(opportunities);
  }

  public List<OutlineStatementOpportunity> getOutliningOpportunities(
        TransformationProbabilities probabilities, IRandom generator) {
    return opportunities.stream().filter(opportunity -> probabilities.outlineStatements(generator))
          .collect(Collectors.toList());
  }

}
