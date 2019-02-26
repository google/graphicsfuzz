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

import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;

public class FunctionCallExprTest {

  private FunctionCallExpr fce;
  private Expr arg1;
  private Expr arg2;
  private Expr arg3;
  private Expr arg4;


  @Before
  public void setUp() {
    arg1 = new BinaryExpr(new IntConstantExpr("1"), new IntConstantExpr("1"), BinOp.ADD);
    arg2 = new BoolConstantExpr(true);
    arg3 = new TypeConstructorExpr("vec2", new FloatConstantExpr("0.0"));
    arg4 = new FunctionCallExpr("voidArgsFunction", new ArrayList<>());
    fce = new FunctionCallExpr("someFunction", arg1, arg2, arg3, arg4);
  }

  @Test
  public void getCallee() throws Exception {
    assertEquals("someFunction", fce.getCallee());
  }

  @Test
  public void setCallee() throws Exception {
    fce.setCallee("other");
    assertEquals("other", fce.getCallee());
  }

  @Test
  public void getArgs() throws Exception {
    assertEquals(arg1, fce.getArgs().get(0));
    assertEquals(arg2, fce.getArgs().get(1));
    assertEquals(arg3, fce.getArgs().get(2));
    assertEquals(arg4, fce.getArgs().get(3));
  }

  @Test
  public void getNumArgs() throws Exception {
    assertEquals(4, fce.getNumArgs());
    assertEquals(fce.getArgs().size(), fce.getNumArgs());
  }

  @Test
  public void getArg() throws Exception {
    assertEquals(arg1, fce.getArg(0));
    assertEquals(arg2, fce.getArg(1));
    assertEquals(arg3, fce.getArg(2));
    assertEquals(arg4, fce.getArg(3));
  }

  @Test
  public void accept() throws Exception {
    assertEquals("someFunction(1 + 1, true, vec2(0.0), voidArgsFunction())",
      fce.getText());
  }

  @Test
  public void testClone() throws Exception {
    FunctionCallExpr fce2 = fce.clone();
    assertEquals(fce.getText(), fce2.getText());
    assertFalse(fce == fce2);
  }

  @Test
  public void getChild() throws Exception {
    assertEquals(fce.getChild(0), fce.getArg(0));
    assertEquals(fce.getChild(1), fce.getArg(1));
    assertEquals(fce.getChild(2), fce.getArg(2));
    assertEquals(fce.getChild(3), fce.getArg(3));
  }

  @Test
  public void setChild() throws Exception {
    fce.setChild(0, arg4);
    fce.setChild(1, arg3);
    fce.setChild(2, arg2);
    fce.setChild(3, arg1);
    assertEquals(arg4, fce.getArg(0));
    assertEquals(arg3, fce.getArg(1));
    assertEquals(arg2, fce.getArg(2));
    assertEquals(arg1, fce.getArg(3));
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(4, fce.getNumChildren());
    assertEquals(fce.getArgs().size(), fce.getNumChildren());
    assertEquals(fce.getNumArgs(), fce.getNumChildren());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    fce.getChild(4);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    fce.setChild(4, arg1);
  }

  @Test
  public void removeArg() {
    fce.removeArg(1);
    assertEquals(3, fce.getNumArgs());
    assertEquals(arg1, fce.getArg(0));
    assertEquals(arg3, fce.getArg(1));
    assertEquals(arg4, fce.getArg(2));
  }

}
