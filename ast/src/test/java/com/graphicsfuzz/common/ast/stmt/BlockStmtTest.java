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

package com.graphicsfuzz.common.ast.stmt;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import java.util.Arrays;
import org.junit.Test;

public class BlockStmtTest {

  @Test
  public void testInsertStmt() {
    BlockStmt b = new BlockStmt(Arrays.asList(NullStmt.INSTANCE), true);
    assertEquals(1, b.getNumStmts());
    b.insertStmt(0, new ExprStmt(BoolConstantExpr.TRUE));
    assertEquals(2, b.getNumStmts());
    assertEquals(BoolConstantExpr.TRUE, ((ExprStmt)b.getStmt(0)).getExpr());
    assertEquals(NullStmt.INSTANCE, b.getStmt(1));
  }

  @Test
  public void testInsertBefore() {
    ExprStmt stmt = new ExprStmt(BoolConstantExpr.TRUE);
    BlockStmt b = new BlockStmt(Arrays.asList(stmt), true);
    assertEquals(1, b.getNumStmts());
    b.insertBefore(stmt, NullStmt.INSTANCE);
    assertEquals(2, b.getNumStmts());
    assertEquals(NullStmt.INSTANCE, b.getStmt(0));
    assertEquals(stmt, b.getStmt(1));
  }

  @Test
  public void testInsertAfter() {
    ExprStmt stmt1 = new ExprStmt(BoolConstantExpr.TRUE);
    ExprStmt stmt2 = new ExprStmt(BoolConstantExpr.FALSE);
    BlockStmt b = new BlockStmt(Arrays.asList(stmt1, stmt2), true);
    assertEquals(2, b.getNumStmts());
    b.insertAfter(stmt1, NullStmt.INSTANCE);
    assertEquals(3, b.getNumStmts());
    b.insertAfter(stmt2, NullStmt.INSTANCE);
    assertEquals(4, b.getNumStmts());
    assertEquals(stmt1, b.getStmt(0));
    assertEquals(NullStmt.INSTANCE, b.getStmt(1));
    assertEquals(stmt2, b.getStmt(2));
    assertEquals(NullStmt.INSTANCE, b.getStmt(3));
  }

}