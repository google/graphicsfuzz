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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;

public class If2DiscardMiner extends TransformationMinerBase<Stmt2Stmt> {

  private final IParentMap parentMap;

  public If2DiscardMiner(TranslationUnit tu) {
    super(tu);
    parentMap = IParentMap.createParentMap(tu);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    addTransformation(new Stmt2Stmt(ifStmt, ifStmt.getThenStmt(), new DiscardStmt()));
    if (ifStmt.hasElseStmt()) {
      addTransformation(new Stmt2Stmt(ifStmt, ifStmt.getElseStmt(), new DiscardStmt()));
    } else {
      addTransformation(new Stmt2Stmt(parentMap.getParent(ifStmt), ifStmt,
          new IfStmt(ifStmt.getCondition(), ifStmt.getThenStmt(), new DiscardStmt())));
    }
  }

}
