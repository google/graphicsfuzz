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

package com.graphicsfuzz.common.ast.expr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class UnaryExprTest {

  private UnaryExpr expr;

  @Before
  public void setup() {
    expr = new UnaryExpr(new VariableIdentifierExpr("x"), UnOp.BNEG);
  }

  @Test
  public void getExpr() throws Exception {
    assertEquals("~ x", expr.getText());
  }

  @Test
  public void getOp() throws Exception {
    assertEquals(UnOp.BNEG, expr.getOp());
  }

  @Test
  public void testClone() throws Exception {
    UnaryExpr theClone = expr.clone();
    assertFalse(expr == theClone);
    assertEquals(theClone.getText(), expr.getText());
  }

  @Test
  public void getChild() throws Exception {
    assertTrue(expr.getChild(0) instanceof VariableIdentifierExpr);
    assertEquals("x", expr.getChild(0).getText());

  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() throws Exception {
    expr.getChild(1);
  }

  @Test
  public void setChild() throws Exception {
    assertEquals("~ x", expr.getText());
    expr.setChild(0, new VariableIdentifierExpr("y"));
    assertEquals("~ y", expr.getText());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() throws Exception {
    expr.setChild(-1, new IntConstantExpr("3"));
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(1, expr.getNumChildren());
  }

}