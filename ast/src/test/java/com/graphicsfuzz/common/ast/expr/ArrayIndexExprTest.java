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

import org.junit.Before;
import org.junit.Test;

public class ArrayIndexExprTest {

  private ArrayIndexExpr expr;

  @Before
  public void setup() {
    expr = new ArrayIndexExpr(new VariableIdentifierExpr("A"),
        new IntConstantExpr("0"));
  }

  @Test
  public void getArray() throws Exception {
    assertEquals("A", expr.getArray().getText());
  }

  @Test
  public void getIndex() throws Exception {
    assertEquals("0", expr.getIndex().getText());
  }

  @Test
  public void testClone() throws Exception {
    ArrayIndexExpr theClone = expr.clone();
    assertFalse(expr == theClone);
    assertEquals(theClone.getText(), expr.getText());
  }

  @Test
  public void getChild() throws Exception {
    assertEquals("A", expr.getChild(0).getText());
    assertEquals("0", expr.getChild(1).getText());
  }

  @Test
  public void setChild() throws Exception {
    assertEquals("A", expr.getChild(0).getText());
    assertEquals("0", expr.getChild(1).getText());
    expr.setChild(0, new VariableIdentifierExpr("B"));
    assertEquals("B", expr.getChild(0).getText());
    assertEquals("0", expr.getChild(1).getText());
    expr.setChild(1, new IntConstantExpr("3"));
    assertEquals("B", expr.getChild(0).getText());
    assertEquals("3", expr.getChild(1).getText());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    expr.getChild(2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    expr.setChild(-1, new VariableIdentifierExpr("T"));
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(2, expr.getNumChildren());
  }

}