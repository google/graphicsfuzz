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
import static org.junit.Assert.assertNotSame;

import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import org.junit.Before;
import org.junit.Test;

public class TernaryExprTest {

  private VariableIdentifierExpr conditionVar;
  private VariableIdentifierExpr thenVar;
  private VariableIdentifierExpr elseVar;

  @Before
  public void setUp() {
    conditionVar = new VariableIdentifierExpr("x");
    thenVar = new VariableIdentifierExpr("y");
    elseVar = new VariableIdentifierExpr("z");
  }

  private TernaryExpr makeTernary() {
    return new TernaryExpr(conditionVar, thenVar, elseVar);
  }

  @Test
  public void getTest() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(conditionVar, te.getTest());
    assertEquals(conditionVar, te.getChild(0));
  }

  @Test
  public void getThenExpr() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(thenVar, te.getThenExpr());
    assertEquals(thenVar, te.getChild(1));
  }

  @Test
  public void getElseExpr() throws Exception {
    TernaryExpr te = makeTernary();
    assertEquals(elseVar, te.getElseExpr());
    assertEquals(elseVar, te.getChild(2));
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
    assertNotSame(te, te2);
    assertNotSame(te.getTest(), te2.getTest());
    assertNotSame(te.getThenExpr(), te2.getThenExpr());
    assertNotSame(te.getElseExpr(), te2.getElseExpr());

    assertEquals(((VariableIdentifierExpr) te.getTest()).getName(),
        ((VariableIdentifierExpr) te2.getTest()).getName());
    assertEquals(((VariableIdentifierExpr) te.getThenExpr()).getName(),
        ((VariableIdentifierExpr) te2.getThenExpr()).getName());
    assertEquals(((VariableIdentifierExpr) te.getElseExpr()).getName(),
        ((VariableIdentifierExpr) te2.getElseExpr()).getName());
  }

  @Test
  public void setChild() throws Exception {
    TernaryExpr te = makeTernary();
    te.setChild(0, elseVar);
    te.setChild(1, conditionVar);
    te.setChild(2, thenVar);
    assertEquals(elseVar, te.getTest());
    assertEquals(conditionVar, te.getThenExpr());
    assertEquals(thenVar, te.getElseExpr());
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
    makeTernary().setChild(3, conditionVar);
  }

}
