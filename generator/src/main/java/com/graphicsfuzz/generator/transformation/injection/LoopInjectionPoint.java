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
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.typing.Scope;
import java.util.ArrayList;
import java.util.List;

public class LoopInjectionPoint extends InjectionPoint {

  private LoopStmt loopStmt;

  public LoopInjectionPoint(LoopStmt loopStmt, FunctionDefinition enclosingFunction,
      Scope scope) {
    super(enclosingFunction, true, scope);
    this.loopStmt = loopStmt;
  }

  @Override
  public void inject(Stmt stmt) {
    assert !(loopStmt.getBody() instanceof BlockStmt);
    List<Stmt> stmts = new ArrayList<>();
    stmts.add(stmt);
    stmts.add(loopStmt.getBody());
    loopStmt.setBody(new BlockStmt(stmts, false));
  }

  @Override
  public Stmt getNextStmt() {
    return loopStmt.getBody();
  }

  @Override
  public boolean hasNextStmt() {
    return true;
  }

  @Override
  public void replaceNext(Stmt stmt) {
    loopStmt.setBody(stmt);
  }

}
