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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TruncateLoops extends StandardVisitor {

  private final int limit;
  private final TranslationUnit tu;
  private final String prefix;
  private int counter;

  public TruncateLoops(int limit, String prefix, TranslationUnit tu) {
    this.limit = limit;
    this.tu = tu;
    this.prefix = prefix;
    counter = 0;
    visit(tu);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    handleLoop(forStmt);
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    handleLoop(whileStmt);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    handleLoop(doStmt);
  }

  private void handleLoop(LoopStmt loopStmt) {
    final IParentMap parentMap = IParentMap.createParentMap(tu);
    final String limiterName = prefix + "_" + Constants.LOOP_LIMITER + counter;
    counter++;

    final DeclarationStmt limiterDeclaration = new DeclarationStmt(
          new VariablesDeclaration(BasicType.INT,
                new VariableDeclInfo(limiterName, null,
                      new Initializer(new IntConstantExpr("0")))));

    final List<Stmt> limitCheckAndIncrement = Arrays.asList(
          new IfStmt(
            new BinaryExpr(
                  new VariableIdentifierExpr(limiterName),
                  new IntConstantExpr(String.valueOf(limit)),
                  BinOp.GE),
            new BlockStmt(Arrays.asList(new BreakStmt()), true),
            null),
          new ExprStmt(new UnaryExpr(
            new VariableIdentifierExpr(limiterName),
            UnOp.POST_INC)));

    if (loopStmt.getBody() instanceof BlockStmt) {
      for (int i = limitCheckAndIncrement.size() - 1; i >= 0; i--) {
        ((BlockStmt) loopStmt.getBody()).insertStmt(0, limitCheckAndIncrement.get(i));
      }
    } else {
      final List<Stmt> newStmts = new ArrayList<>();
      newStmts.addAll(limitCheckAndIncrement);
      newStmts.add(loopStmt.getBody());
      loopStmt.setBody(new BlockStmt(newStmts, loopStmt instanceof DoStmt));
    }

    final BlockStmt replacementBlock = new BlockStmt(
          Arrays.asList(limiterDeclaration, loopStmt), true);
    parentMap.getParent(loopStmt).replaceChild(loopStmt, replacementBlock);
  }

}
