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
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;

class ShadowChecker extends ScopeTreeBuilder {

  private final BlockStmt blockOfInterest;
  private final String nameOfInterest;

  private boolean inBlock = false;
  private ScopeEntry possiblyShadowedScopeEntry = null;
  private boolean ok = true;

  ShadowChecker(BlockStmt blockOfInterest, String nameOfInterest) {
    this.blockOfInterest = blockOfInterest;
    this.nameOfInterest = nameOfInterest;
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    if (stmt == blockOfInterest) {
      inBlock = true;
      possiblyShadowedScopeEntry = currentScope.lookupScopeEntry(nameOfInterest);
    }
    super.visitBlockStmt(stmt);
    if (stmt == blockOfInterest) {
      inBlock = false;
      possiblyShadowedScopeEntry = null;
    }
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    if (inBlock && possiblyShadowedScopeEntry != null
          && currentScope.lookupScopeEntry(variableIdentifierExpr.getName())
          == possiblyShadowedScopeEntry) {
      ok = false;
    }
  }

  boolean isOk(TranslationUnit tu) {
    visit(tu);
    return ok;
  }

}
