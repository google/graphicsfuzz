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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class VariableDeclToExprReductionOpportunity extends AbstractReductionOpportunity {

  private final VariableDeclInfo variableDeclInfo;
  private final DeclarationStmt declarationStmt;
  private final IAstNode parent;

  VariableDeclToExprReductionOpportunity(VariableDeclInfo variableDeclInfo, IAstNode parent,
                                         DeclarationStmt declarationStmt, VisitationDepth depth) {
    super(depth);
    this.variableDeclInfo = variableDeclInfo;
    this.parent = parent;
    this.declarationStmt = declarationStmt;
  }

  @Override
  void applyReductionImpl() {
    // Given the variable declaration info, we unset its initializer and derive a new binary
    // expression which will be inserted right after the declaration in the block statement.
    assert variableDeclInfo.getInitializer() instanceof ScalarInitializer;
    final BinaryExpr binaryExpr = new BinaryExpr(
        new VariableIdentifierExpr(variableDeclInfo.getName()),
        ((ScalarInitializer) variableDeclInfo.getInitializer()).getExpr(),
        BinOp.ASSIGN
    );
    ((BlockStmt) parent).insertAfter(declarationStmt, new ExprStmt(binaryExpr));
    variableDeclInfo.setInitializer(null);
  }

  @Override
  public boolean preconditionHolds() {
    return parent instanceof BlockStmt
        && parent.hasChild(declarationStmt)
        && variableDeclInfo.hasInitializer();
  }
}
