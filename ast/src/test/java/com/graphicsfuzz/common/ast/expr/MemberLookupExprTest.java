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
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import org.junit.Test;

public class MemberLookupExprTest {

  @Test
  public void getStructure() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    assertEquals(v, mle.getStructure());
  }

  @Test
  public void setStructure() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("w");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    mle.setStructure(w);
    assertEquals(w, mle.getStructure());
  }

  @Test
  public void getMember() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    assertEquals("foo", mle.getMember());
  }

  @Test
  public void setMember() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    assertEquals("foo", mle.getMember());
    mle.setMember("bar");
    assertEquals("bar", mle.getMember());
  }

  @Test
  public void accept() throws Exception {
    new StandardVisitor() {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals("foo", memberLookupExpr.getMember());
        assertTrue(memberLookupExpr.getStructure() instanceof VariableIdentifierExpr);
        assertEquals("v", ((VariableIdentifierExpr) memberLookupExpr.getStructure()).getName());
      }
    }.visit(new MemberLookupExpr(new VariableIdentifierExpr("v"), "foo"));
  }

  @Test
  public void testClone() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    MemberLookupExpr mle2 = mle.clone();
    assertNotSame(mle, mle2);
    assertEquals(mle.getMember(), mle2.getMember());
    assertEquals(((VariableIdentifierExpr) mle.getStructure()).getName(),
        ((VariableIdentifierExpr) mle2.getStructure()).getName());
    assertNotSame(mle.getStructure(), mle2.getStructure());
  }

  @Test
  public void getChild() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    assertEquals(v, mle.getChild(0));
    assertEquals(mle.getStructure(), mle.getChild(0));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildException() throws Exception {
    new MemberLookupExpr(new VariableIdentifierExpr("v"), "foo").getChild(1);
  }

  @Test
  public void setChild() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("w");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    mle.setChild(0, w);
    assertEquals(w, mle.getStructure());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildException() throws Exception {
    new MemberLookupExpr(new VariableIdentifierExpr("v"), "foo")
        .setChild(1, new VariableIdentifierExpr("w"));
  }

  @Test
  public void getNumChildren() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    MemberLookupExpr mle = new MemberLookupExpr(
        v,
        "foo");
    assertEquals(1, mle.getNumChildren());
  }

}
