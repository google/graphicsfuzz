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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;
import com.graphicsfuzz.util.Constants;
import java.util.HashSet;
import java.util.Set;

public class OutlineStatementMutationFinder extends MutationFinderBase<OutlineStatementMutation> {

  private final IdGenerator idGenerator;

  public OutlineStatementMutationFinder(TranslationUnit tu) {
    super(tu);
    this.idGenerator = new IdGenerator(getIdsAlreadyUsedForOutlining());
  }

  private Set<Integer> getIdsAlreadyUsedForOutlining() {
    final Set<Integer> result = new HashSet<>();
    new StandardVisitor() {
      @Override
      public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
        if (functionPrototype.getName().startsWith(Constants.OUTLINED_FUNCTION_PREFIX)) {
          result.add(new Integer(
              functionPrototype.getName().substring(Constants.OUTLINED_FUNCTION_PREFIX.length())));
        }
      }
    }.visit(getTranslationUnit());
    return result;
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    super.visitExprStmt(exprStmt);
    if (!OutlineStatementMutation.isAssignment(exprStmt)) {
      return;
    }
    BinaryExpr be = (BinaryExpr) exprStmt.getExpr();
    if (!OutlineStatementMutation.assignsDirectlyToVariable(be)) {
      return;
    }
    if (referencesArray(be.getRhs(), getCurrentScope())) {
      return;
    }
    addMutation(new OutlineStatementMutation(exprStmt, getCurrentScope(), getTranslationUnit(),
          getEnclosingFunction(), idGenerator));
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

}
