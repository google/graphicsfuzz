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
import org.junit.Test;

public class BinaryExprTest {

  @Test
  public void getLhs() throws Exception {
    VariableIdentifierExpr vie = new VariableIdentifierExpr("x");
    BinaryExpr be = new BinaryExpr(
        vie,
        BoolConstantExpr.TRUE,
        BinOp.LAND);
    assertEquals(vie, be.getLhs());
  }

  @Test
  public void getRhs() throws Exception {
    FloatConstantExpr fce = new FloatConstantExpr("10.2");
    BinaryExpr be = new BinaryExpr(
        new TypeConstructorExpr("float", new IntConstantExpr("1")),
        fce,
        BinOp.MOD);
    assertEquals(fce, be.getRhs());
  }

  @Test
  public void getOp() throws Exception {
    assertEquals(BinOp.ADD_ASSIGN,
        new BinaryExpr(new VariableIdentifierExpr("x"),
            new UIntConstantExpr("3u"),
            BinOp.ADD_ASSIGN).getOp());
  }

  @Test
  public void accept() throws Exception {
    BinaryExpr be = new BinaryExpr(new IntConstantExpr("1"),
        new IntConstantExpr("1"), BinOp.ADD);
    BinaryExpr theExpr = new BinaryExpr(be.clone(), be.clone(), BinOp.ADD);
    theExpr = new BinaryExpr(theExpr.clone(), theExpr.clone(), BinOp.ADD);

    int numBinariesInExpr =
      new StandardVisitor() {
        private int numBinaries;

        @Override
        public void visitBinaryExpr(BinaryExpr binaryExpr) {
          super.visitBinaryExpr(binaryExpr);
          numBinaries++;
        }

        public int getNumBinaries(Expr e) {
          numBinaries = 0;
          visit(e);
          return numBinaries;
        }
      }.getNumBinaries(theExpr);

    assertEquals(7, numBinariesInExpr);

  }

  @Test
  public void testClone() {
    BinaryExpr be = new BinaryExpr(new VariableIdentifierExpr("x"),
        new VariableIdentifierExpr("y"), BinOp.ADD);
    BinaryExpr be2 = be.clone();
    assertFalse(be == be2);
    assertFalse(be.getLhs() == be2.getLhs());
    assertEquals(((VariableIdentifierExpr) be.getLhs()).getName(),
        ((VariableIdentifierExpr) be2.getLhs()).getName());
    assertEquals(((VariableIdentifierExpr) be.getRhs()).getName(),
        ((VariableIdentifierExpr) be2.getRhs()).getName());
  }

  @Test
  public void getAndSetChild() throws Exception {
    VariableIdentifierExpr e1 = new VariableIdentifierExpr("x");
    VariableIdentifierExpr e2 = new VariableIdentifierExpr("y");

    BinaryExpr be = new BinaryExpr(e1, e2, BinOp.MUL);

    assertEquals(e1, be.getChild(0));
    assertEquals(be.getLhs(), be.getChild(0));

    assertEquals(e2, be.getChild(1));
    assertEquals(be.getRhs(), be.getChild(1));

    be.setChild(0, e2);
    assertEquals(be.getChild(0), be.getChild(1));
    be.setChild(1, e1);

    assertEquals(e2, be.getChild(0));
    assertEquals(e1, be.getChild(1));

  }

  @Test
  public void getNumChildren() throws Exception {
    BinaryExpr be = new BinaryExpr(BoolConstantExpr.TRUE,
        BoolConstantExpr.FALSE, BinOp.LXOR);
    assertEquals(2, be.getNumChildren());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    new BinaryExpr(BoolConstantExpr.TRUE,
        BoolConstantExpr.FALSE, BinOp.LXOR).getChild(2);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    new BinaryExpr(BoolConstantExpr.TRUE,
        BoolConstantExpr.FALSE, BinOp.LXOR).setChild(2,
          BoolConstantExpr.FALSE);
  }

}