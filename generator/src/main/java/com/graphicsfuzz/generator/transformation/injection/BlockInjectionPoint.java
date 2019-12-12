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

package com.graphicsfuzz.generator.transformation.injection;

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.typing.Scope;

public class BlockInjectionPoint extends InjectionPoint {

  private BlockStmt blockStmt;
  private Stmt nextStmt; // null if there is no next statement

  public BlockInjectionPoint(BlockStmt blockStmt, Stmt nextStmt,
      FunctionDefinition enclosingFunction, boolean inLoop, boolean inSwitch, Scope scope) {
    super(enclosingFunction, inLoop, inSwitch, scope);
    this.blockStmt = blockStmt;
    this.nextStmt = nextStmt;
  }

  @Override
  public void inject(Stmt stmt) {
    if (nextStmt == null) {
      blockStmt.insertStmt(blockStmt.getNumStmts(), stmt);
    } else {
      blockStmt.insertBefore(nextStmt, stmt);
    }
  }

  @Override
  public Stmt getNextStmt() {
    assert hasNextStmt();
    return nextStmt;
  }

  @Override
  public boolean hasNextStmt() {
    return nextStmt != null;
  }

  @Override
  public void replaceNext(Stmt stmt) {
    assert hasNextStmt();
    for (int i = 0; i < blockStmt.getNumStmts(); i++) {
      if (blockStmt.getStmt(i) == nextStmt) {
        blockStmt.setStmt(i, stmt);
      }
    }
  }

}
