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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;

/**
 * Finds opportunities to remove a statement.
 */

public class RemoveStmtMutationFinder extends MutationFinderBase<RemoveStmtMutation> {

  public RemoveStmtMutationFinder(TranslationUnit tu) {
    super(tu);
  }

  @Override
  protected void visitChildOfBlock(BlockStmt parent, Stmt child) {
    if (!(child instanceof DeclarationStmt)) {
      addMutation(new RemoveStmtMutation(parent, child));
    }
  }

}
