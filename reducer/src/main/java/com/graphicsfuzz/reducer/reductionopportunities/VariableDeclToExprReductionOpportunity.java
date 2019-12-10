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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class VariableDeclToExprReductionOpportunity extends AbstractReductionOpportunity {

  // The initialized variable declaration info.
  private final VariableDeclInfo variableDeclInfo;
  // The parent of variableDeclInfo.
  private final DeclarationStmt declarationStmt;
  // The block in which the declaration statement resides.
  private final BlockStmt enclosingBlock;

  VariableDeclToExprReductionOpportunity(VariableDeclInfo variableDeclInfo,
                                         BlockStmt enclosingBlock,
                                         DeclarationStmt declarationStmt, VisitationDepth depth) {
    super(depth);
    this.variableDeclInfo = variableDeclInfo;
    this.enclosingBlock = enclosingBlock;
    this.declarationStmt = declarationStmt;
  }

  @Override
  void applyReductionImpl() {
    // Given the variable declaration info, we unset its initializer and derive a new assignment
    // statement which will be inserted right after the declaration in the block statement.
    assert variableDeclInfo.hasInitializer();
    final BinaryExpr binaryExpr = new BinaryExpr(
        new VariableIdentifierExpr(variableDeclInfo.getName()),
        (variableDeclInfo.getInitializer()).getExpr(),
        BinOp.ASSIGN
    );
    enclosingBlock.insertAfter(declarationStmt, new ExprStmt(binaryExpr));
    variableDeclInfo.setInitializer(null);
  }

  @Override
  public boolean preconditionHolds() {
    return enclosingBlock.hasChild(declarationStmt)
        && variableDeclInfo.hasInitializer();
  }
}
