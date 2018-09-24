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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

class LocalDeclarationReductionOpportunity extends DeclarationReductionOpportunity {

  public LocalDeclarationReductionOpportunity(VariableDeclInfo variableDeclInfo,
      IParentMap parentMap, VisitationDepth depth) {
    super(variableDeclInfo, parentMap, depth);
  }

  @Override
  void removeVariablesDeclaration(VariablesDeclaration node) {
    IAstNode nodeParent = parentMap.getParent(node);
    if (!(nodeParent instanceof DeclarationStmt)) {
      throw new FailedReductionException(
          "We only know, so far, how to eliminate declarations from DeclarationStmts; found " + node
              .getClass());
    }
    IAstNode nodeGrandparent = parentMap.getParent(nodeParent);
    if (!(nodeGrandparent instanceof BlockStmt)) {
      throw new FailedReductionException(
          "We only know, so far, how to eliminate DeclarationStmt from BlockStmt; found "
              + nodeGrandparent);
    }
    BlockStmt blockStmt = (BlockStmt) nodeGrandparent;
    for (int i = 0; i < blockStmt.getNumStmts(); i++) {
      if (blockStmt.getStmt(i) == nodeParent) {
        blockStmt.removeStmt(i);
        return;
      }
    }
    assert false; // Should not get here, since we should find and remove the statement
  }

}
