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

public class TernaryExprTest {

  private VariableIdentifierExpr x;
  private VariableIdentifierExpr y;
  private VariableIdentifierExpr z;

  @Before
  public void setUp() {
    x = new VariableIdentifierExpr("x");
    y = new VariableIdentifierExpr("y");
    z = new VariableIdentifierExpr("z");
  }

  private TernaryExpr makeTernary() {
    return new TernaryExpr(x, y, z);
  }

  @Test
  public void getTest() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(x, te.getTest());
    assertEquals(x, te.getChild(0));
  }

  @Test
  public void getThenExpr() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(y, te.getThenExpr());
    assertEquals(y, te.getChild(1));
  }

  @Test
  public void getElseExpr() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(z, te.getElseExpr());
    assertEquals(z, te.getChild(2));
  }

  @Test
  public void accept() throws Exception {
    TernaryExpr te = new TernaryExpr(
        makeTernary().clone(),
        makeTernary().clone(),
        makeTernary().clone());
    assertEquals(4,
        new StandardVisitor() {
          private int numTernaries;

          @Override
          public void visitTernaryExpr(TernaryExpr ternaryExpr) {
            super.visitTernaryExpr(ternaryExpr);
            numTernaries++;
          }

          public int getNumTernaries(TernaryExpr node) {
            numTernaries = 0;
            visit(node);
            return numTernaries;
          }

        }.getNumTernaries(te)
    );
  }

  @Test
  public void testClone() throws Exception {
    TernaryExpr te = makeTernary();
    TernaryExpr te2 = te.clone();
    assertFalse(te == te2);
    assertFalse(te.getTest() == te2.getTest());
    assertFalse(te.getThenExpr() == te2.getThenExpr());
    assertFalse(te.getElseExpr() == te2.getElseExpr());

    assertEquals(((VariableIdentifierExpr) te.getTest()).getName(), ((VariableIdentifierExpr) te2.getTest()).getName());
    assertEquals(((VariableIdentifierExpr) te.getThenExpr()).getName(), ((VariableIdentifierExpr) te2.getThenExpr()).getName());
    assertEquals(((VariableIdentifierExpr) te.getElseExpr()).getName(), ((VariableIdentifierExpr) te2.getElseExpr()).getName());
  }

  @Test
  public void setChild() throws Exception {
    TernaryExpr te = makeTernary();
    te.setChild(0, z);
    te.setChild(1, x);
    te.setChild(2, y);
    assertEquals(z, te.getTest());
    assertEquals(x, te.getThenExpr());
    assertEquals(y, te.getElseExpr());
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(3, makeTernary().getNumChildren());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    makeTernary().getChild(3);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    makeTernary().setChild(3, x);
  }

}