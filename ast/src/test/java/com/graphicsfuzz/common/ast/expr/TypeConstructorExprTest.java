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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TypeConstructorExprTest {

  @Test
  public void getTypename() throws Exception {
    TypeConstructorExpr e = new TypeConstructorExpr("foo",
        new BinaryExpr(new IntConstantExpr("2"), new IntConstantExpr("1"), BinOp.MOD),
        BoolConstantExpr.TRUE);
    assertEquals("foo", e.getTypename());
  }

  @Test
  public void getArgs() throws Exception {
    List<Expr> args = exprsList();

    TypeConstructorExpr e = new TypeConstructorExpr("foo",
        args);

    for (int i = 0; i < args.size(); i++) {
      assertTrue(args.get(i) == e.getArgs().get(i));
    }

  }

  @Test
  public void getArgAndGetChild() throws Exception {
    List<Expr> args = exprsList();

    TypeConstructorExpr e = new TypeConstructorExpr("foo",
        args);

    for (int i = 0; i < args.size(); i++) {
      assertTrue(args.get(i) == e.getArg(i));
      assertTrue(args.get(i) == e.getChild(i));
    }

  }

  @Test
  public void removeArg() throws Exception {
    List<Expr> args = Arrays.asList(BoolConstantExpr.TRUE, BoolConstantExpr.FALSE,
        new IntConstantExpr("3"),
        new IntConstantExpr("4"));

    TypeConstructorExpr e = new TypeConstructorExpr("foo",
        args);

    assertEquals(4, args.size());
    assertEquals(4, e.getArgs().size());

    e.removeArg(1);

    assertEquals(4, args.size());
    assertEquals(3, e.getArgs().size());

    assertEquals(args.get(0), e.getArg(0));
    assertEquals(args.get(2), e.getArg(1));
    assertEquals(args.get(3), e.getArg(2));

  }

  @Test
  public void getNumArs() {
    assertEquals(3, new TypeConstructorExpr("foo", exprsList()).getNumArgs());
  }

  @Test
  public void getNumChildren() {
    assertEquals(3, new TypeConstructorExpr("foo", exprsList()).getNumChildren());
  }

  @Test
  public void testClone() {
    TypeConstructorExpr tce = new TypeConstructorExpr(
        "hey",
        Arrays.asList(new VariableIdentifierExpr("x"),
            new VariableIdentifierExpr("y")));
    TypeConstructorExpr tce2 = tce.clone();
    assertFalse(tce == tce2);
    assertEquals(tce.getTypename(), tce2.getTypename());
    assertFalse(tce.getArg(0) == tce2.getArg(0));
    assertFalse(tce.getArg(1) == tce2.getArg(1));
    assertEquals(((VariableIdentifierExpr) tce.getArg(0)).getName(), ((VariableIdentifierExpr) tce2.getArg(0)).getName());
    assertEquals(((VariableIdentifierExpr) tce.getArg(1)).getName(), ((VariableIdentifierExpr) tce2.getArg(1)).getName());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    TypeConstructorExpr tce = new TypeConstructorExpr("hi",
        new IntConstantExpr("5"));
    tce.getChild(1);
  }

  @Test
  public void setChild() {
    TypeConstructorExpr tce = new TypeConstructorExpr("hi",
        new IntConstantExpr("5"));
    IntConstantExpr expr = new IntConstantExpr("6");
    tce.setChild(0, expr);
    assertEquals(expr, tce.getArg(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    TypeConstructorExpr tce = new TypeConstructorExpr("hi",
        new IntConstantExpr("5"));
    tce.setChild(1, new IntConstantExpr("6"));
  }

  @Test
  public void accept() {
    TypeConstructorExpr tce = new TypeConstructorExpr("foo", exprsList());
    assertEquals("foo(2.0 * 3.6, true, s.f)", tce.getText());
  }

  @Test
  public void insertArg() {
    TypeConstructorExpr tce = new TypeConstructorExpr("foo", exprsList());
    tce.insertArg(1, new FloatConstantExpr("12.34"));
    assertEquals("foo(2.0 * 3.6, 12.34, true, s.f)", tce.getText());
  }

  private static List<Expr> exprsList() {
    return Arrays.asList(new BinaryExpr(new FloatConstantExpr("2.0"),
            new FloatConstantExpr("3.6"), BinOp.MUL),
        BoolConstantExpr.TRUE,
        new MemberLookupExpr(new VariableIdentifierExpr("s"), "f"));
  }

}