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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.ArrayList;
import org.junit.Test;

public class ForStmtTest {

  @Test
  public void replaceChild() {
    final ExprStmt init = new ExprStmt(new IntConstantExpr("0"));
    final IntConstantExpr condition = new IntConstantExpr("1");
    final IntConstantExpr increment = new IntConstantExpr("2");
    final BlockStmt body = new BlockStmt(new ArrayList<>(), false);
    final ForStmt forStmt = new ForStmt(
        init,
        condition,
        increment,
        body);
    final ExprStmt newInit = new ExprStmt(new IntConstantExpr("3"));
    final IntConstantExpr newCondition = new IntConstantExpr("4");
    final IntConstantExpr newIncrement = new IntConstantExpr("5");
    final BlockStmt newBody = new BlockStmt(new ArrayList<>(), false);

    forStmt.replaceChild(init, newInit);
    forStmt.replaceChild(condition, newCondition);
    forStmt.replaceChild(increment, newIncrement);
    forStmt.replaceChild(body, newBody);

    assertTrue(newInit == forStmt.getInit());
    assertTrue(newCondition == forStmt.getCondition());
    assertTrue(newIncrement == forStmt.getIncrement());
    assertTrue(newBody == forStmt.getBody());

  }

  @Test
  public void testInitIsNullStmt() throws Exception {
    // Tests that the init of a for loop with essentially no initializer is a null statement,
    // rather than actually being null.
    final TranslationUnit tu = ParseHelper.parse("void main() { for( ; 1; 1) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
        .getStmt(0);
    assertTrue(stmt.getInit() instanceof NullStmt);
  }

  @Test
  public void testHasIncrementButNoCond() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for( 1; ; 1) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
        .getStmt(0);
    assertFalse(stmt
        .hasCondition());
    assertFalse(stmt
        .hasCondition());
    assertTrue(stmt
        .hasIncrement());

  }

  @Test
  public void testHasCondButNoIncrement() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for( 1; 1; ) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
        .getStmt(0);
    assertTrue(stmt
        .hasCondition());
    assertFalse(stmt
        .hasIncrement());
  }

  @Test
  public void testCloneEmptyFor() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (;;) ; }");
    final TranslationUnit tu2 = tu.clone();
    CompareAstsDuplicate.assertEqualAsts(tu, tu2);
  }

  @Test
  public void testEmptyFor() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (;;) ; }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
        .getStmt(0);
    assertTrue(stmt.getInit() instanceof NullStmt);
    assertFalse(stmt
        .hasCondition());
    assertFalse(stmt
        .hasIncrement());
  }

  @Test
  public void testOnlyHasInit() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (1; ; ) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
            .getStmt(0);
    assertFalse(stmt.getInit() instanceof NullStmt);
    assertFalse(stmt
        .hasCondition());
    assertFalse(stmt
        .hasIncrement());
  }

  @Test
  public void testOnlyHasCondition() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (; 1; ) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
            .getStmt(0);
    assertTrue(stmt.getInit() instanceof NullStmt);
    assertTrue(stmt
        .hasCondition());
    assertFalse(stmt
        .hasIncrement());
  }

  @Test
  public void testOnlyHasIncrement() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (; ; 1) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
            .getStmt(0);
    assertTrue(stmt.getInit() instanceof NullStmt);
    assertFalse(stmt
        .hasCondition());
    assertTrue(stmt
        .hasIncrement());
  }

  @Test
  public void testHasAllFields() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() { for (1; 1; 1) { } }");
    final ForStmt stmt =
        (ForStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
            .getStmt(0);
    assertFalse(stmt.getInit() instanceof NullStmt);
    assertTrue(stmt
        .hasCondition());
    assertTrue(stmt
        .hasIncrement());
  }

}
