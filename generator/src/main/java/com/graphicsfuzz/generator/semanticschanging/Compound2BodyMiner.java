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
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import java.util.Arrays;

public class Compound2BodyMiner extends TransformationMinerBase<Stmt2Stmt> {

  private final IParentMap parentMap;

  public Compound2BodyMiner(TranslationUnit tu) {
    super(tu);
    parentMap = IParentMap.createParentMap(tu);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    super.visitIfStmt(ifStmt);
    addTransformation(new Stmt2Stmt(parentMap.getParent(ifStmt), ifStmt, ifStmt.getThenStmt()));
    if (ifStmt.hasElseStmt()) {
      addTransformation(new Stmt2Stmt(parentMap.getParent(ifStmt), ifStmt, ifStmt.getElseStmt()));
    }
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    final BlockStmt replacement = new BlockStmt(
          Arrays.asList(forStmt.getInit()), true);
    for (Stmt stmt :
          forStmt.getBody() instanceof BlockStmt
                ? ((BlockStmt) forStmt.getBody()).getStmts()
                : Arrays.asList(forStmt.getBody())) {
      replacement.addStmt(stmt);
    }
    addTransformation(new Stmt2Stmt(parentMap.getParent(forStmt), forStmt, replacement));
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    addTransformation(
          new Stmt2Stmt(parentMap.getParent(whileStmt), whileStmt, whileStmt.getBody()));
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    addTransformation(
          new Stmt2Stmt(parentMap.getParent(doStmt), doStmt, doStmt.getBody()));
  }

}
