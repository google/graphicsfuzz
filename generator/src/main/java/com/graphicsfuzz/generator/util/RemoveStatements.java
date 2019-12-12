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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class RemoveStatements extends StandardVisitor {

  private final Predicate<Stmt> shouldRemove;
  private final Function<Stmt, Stmt> replaceWith;

  public RemoveStatements(Predicate<Stmt> shouldRemove,
      Function<Stmt, Stmt> replaceWith, IAstNode node) {
    this.shouldRemove = shouldRemove;
    this.replaceWith = replaceWith;
    visit(node);
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    List<Stmt> newStmts = new ArrayList<>();
    for (Stmt s : stmt.getStmts()) {
      if (shouldRemove.test(s)) {
        newStmts.add(getReplacementStmt(s));
        continue;
      }
      newStmts.add(s);
    }
    stmt.setStmts(newStmts);
    super.visitBlockStmt(stmt);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    handleLoop(doStmt);
    super.visitDoStmt(doStmt);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    handleLoop(forStmt);
    super.visitForStmt(forStmt);
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    handleLoop(whileStmt);
    super.visitWhileStmt(whileStmt);
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    if (shouldRemove.test(ifStmt.getThenStmt())) {
      ifStmt.setThenStmt(getReplacementStmt(ifStmt.getThenStmt()));
    }
    if (ifStmt.hasElseStmt()) {
      if (shouldRemove.test(ifStmt.getElseStmt())) {
        ifStmt.setElseStmt(getReplacementStmt(ifStmt.getElseStmt()));
      }
    }
    super.visitIfStmt(ifStmt);
  }

  private void handleLoop(LoopStmt loop) {
    final Stmt body = loop.getBody();
    if (shouldRemove.test(body)) {
      Stmt newStmt = getReplacementStmt(body);
      loop.setBody(newStmt);
    }
  }

  private Stmt getReplacementStmt(Stmt stmt) {
    return replaceWith.apply(stmt);
  }

  /**
   * Provides a statement of the form "1;", which is what most statements are replaced with.
   */
  static Stmt makeIntConstantExprStmt() {
    return new ExprStmt(new IntConstantExpr("1"));
  }

}
