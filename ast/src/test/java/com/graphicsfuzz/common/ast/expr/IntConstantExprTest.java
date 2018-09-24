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

import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import org.junit.Test;

public class IntConstantExprTest {

  @Test
  public void getValue() throws Exception {
    IntConstantExpr ec = new IntConstantExpr("-1");
    assertEquals("-1", ec.getValue());
  }

  @Test
  public void accept() throws Exception {
    new StandardVisitor() {
      @Override
      public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
        super.visitIntConstantExpr(intConstantExpr);
        assertEquals("42", intConstantExpr.getValue());
      }
    }.visit(new IntConstantExpr("42"));
  }

  @Test
  public void testClone() throws Exception {
    IntConstantExpr ec1 = new IntConstantExpr("10");
    IntConstantExpr ec2 = ec1.clone();
    assertEquals(ec1.getValue(), ec2.getValue());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChild() throws Exception {
    new IntConstantExpr("2").getChild(0);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChild() throws Exception {
    new IntConstantExpr("2").setChild(0,
        new IntConstantExpr("3"));
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(0, new IntConstantExpr("2").getNumChildren());
  }

}