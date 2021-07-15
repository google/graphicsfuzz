/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import org.junit.Test;

public class LengthExprTest {

  @Test
  public void getReceiver() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    LengthExpr le = new LengthExpr(v);
    assertEquals(v, le.getReceiver());
  }

  @Test
  public void setReceiver() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("w");
    LengthExpr le = new LengthExpr(v);
    le.setReceiver(w);
    assertEquals(w, le.getReceiver());
  }

  @Test
  public void accept() throws Exception {
    new StandardVisitor() {
      @Override
      public void visitLengthExpr(LengthExpr memberLookupExpr) {
        super.visitLengthExpr(memberLookupExpr);
        assertTrue(memberLookupExpr.getReceiver() instanceof VariableIdentifierExpr);
        assertEquals("v", ((VariableIdentifierExpr) memberLookupExpr.getReceiver()).getName());
      }
    }.visit(new LengthExpr(new VariableIdentifierExpr("v")));
  }

  @Test
  public void testClone() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    LengthExpr le = new LengthExpr(v);
    LengthExpr le2 = le.clone();
    assertNotSame(le, le2);
    assertEquals(((VariableIdentifierExpr) le.getReceiver()).getName(),
        ((VariableIdentifierExpr) le2.getReceiver()).getName());
    assertNotSame(le.getReceiver(), le2.getReceiver());
  }

  @Test
  public void getChild() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    LengthExpr le = new LengthExpr(v);
    assertEquals(v, le.getChild(0));
    assertEquals(le.getReceiver(), le.getChild(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildException() throws Exception {
    new LengthExpr(new VariableIdentifierExpr("v")).getChild(1);
  }

  @Test
  public void setChild() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("w");
    LengthExpr le = new LengthExpr(v);
    le.setChild(0, w);
    assertEquals(w, le.getReceiver());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildException() throws Exception {
    new LengthExpr(new VariableIdentifierExpr("v"))
        .setChild(1, new VariableIdentifierExpr("w"));
  }

  @Test
  public void getNumChildren() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    LengthExpr le = new LengthExpr(v);
    assertEquals(1, le.getNumChildren());
  }

}
