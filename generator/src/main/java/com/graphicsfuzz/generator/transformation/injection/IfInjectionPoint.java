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
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.typing.Scope;
import java.util.ArrayList;
import java.util.List;

public class IfInjectionPoint extends InjectionPoint {

  private IfStmt ifStmt;
  private boolean chooseThen;

  public IfInjectionPoint(IfStmt ifStmt, boolean chooseThen, FunctionDefinition enclosingFunction,
      boolean inLoop, Scope scope) {
    super(enclosingFunction, inLoop, scope);
    this.ifStmt = ifStmt;
    this.chooseThen = chooseThen;
  }

  @Override
  public void inject(Stmt stmt) {
    if (chooseThen) {
      assert !(ifStmt.getThenStmt() instanceof BlockStmt);
      List<Stmt> stmts = new ArrayList<>();
      stmts.add(stmt);
      stmts.add(ifStmt.getThenStmt());
      ifStmt.setThenStmt(new BlockStmt(stmts, false));
    } else {
      assert ifStmt.hasElseStmt();
      assert !(ifStmt.getElseStmt() instanceof BlockStmt);
      List<Stmt> stmts = new ArrayList<>();
      stmts.add(stmt);
      stmts.add(ifStmt.getElseStmt());
      ifStmt.setElseStmt(new BlockStmt(stmts, false));
    }

  }

  @Override
  public Stmt getNextStmt() {
    return chooseThen ? ifStmt.getThenStmt() : ifStmt.getElseStmt();
  }

  @Override
  public boolean hasNextStmt() {
    return true;
  }

  @Override
  public void replaceNext(Stmt stmt) {
    if (chooseThen) {
      ifStmt.setThenStmt(stmt);
    } else {
      ifStmt.setElseStmt(stmt);
    }
  }

}
