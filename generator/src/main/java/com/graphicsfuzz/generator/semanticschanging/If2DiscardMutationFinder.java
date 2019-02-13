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
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;
import com.graphicsfuzz.generator.mutateapi.Stmt2StmtMutation;

public class If2DiscardMutationFinder extends MutationFinderBase<Stmt2StmtMutation> {

  private final IParentMap parentMap;

  public If2DiscardMutationFinder(TranslationUnit tu) {
    super(tu);
    parentMap = IParentMap.createParentMap(tu);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    addMutation(new Stmt2StmtMutation(ifStmt, ifStmt.getThenStmt(), () -> new DiscardStmt()));
    if (ifStmt.hasElseStmt()) {
      addMutation(new Stmt2StmtMutation(ifStmt, ifStmt.getElseStmt(), () -> new DiscardStmt()));
    } else {
      addMutation(new Stmt2StmtMutation(parentMap.getParent(ifStmt), ifStmt,
          () -> new IfStmt(ifStmt.getCondition(), ifStmt.getThenStmt(), new DiscardStmt())));
    }
  }

}
