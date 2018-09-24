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

import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import org.junit.Before;
import org.junit.Test;

public class ParenExprTest {

  private VariableIdentifierExpr x;
  private ParenExpr pe;

  @Before
  public void setUp() {
    x = new VariableIdentifierExpr("x");
    pe = new ParenExpr(x);
  }

  @Test
  public void getExpr() throws Exception {
    assertEquals(x, pe.getExpr());
  }

  @Test
  public void accept() throws Exception {
    ParenExpr nested =
      new ParenExpr(
          new ParenExpr(
              new ParenExpr(
                  new ParenExpr(
                      new ParenExpr(
                          x)))));
    assertEquals(5,
        new StandardVisitor() {

          private int numParens;

          @Override
          public void visitParenExpr(ParenExpr parenExpr) {
            super.visitParenExpr(parenExpr);
            numParens++;
          }

          public int getNumParens(ParenExpr expr) {
            numParens = 0;
            visit(expr);
            return numParens;
          }
        }.getNumParens(nested)
    );


  }

  @Test
  public void testClone() throws Exception {
    ParenExpr pe2 = pe.clone();
    assertFalse(pe == pe2);
    assertFalse(pe.getExpr() == pe2.getExpr());
    assertEquals(((VariableIdentifierExpr) pe.getExpr()).getName(), ((VariableIdentifierExpr) pe2.getExpr()).getName());
  }

  @Test
  public void getChild() throws Exception {
    assertEquals(x, pe.getChild(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() throws Exception {
    pe.getChild(1);
  }

  @Test
  public void setChild() throws Exception {
    VariableIdentifierExpr y = new VariableIdentifierExpr("y");
    pe.setChild(0, y);
    assertEquals(y, pe.getChild(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() throws Exception {
    VariableIdentifierExpr y = new VariableIdentifierExpr("y");
    pe.setChild(1, y);
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(1, pe.getNumChildren());
  }

}