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

import com.graphicsfuzz.common.ast.ChildDoesNotExistException;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import org.junit.Test;

public class ExprTest {

  @Test
  public void replaceChild() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("v");
    Expr pe = new ParenExpr(v);
    assertEquals(v, pe.getChild(0));
    pe.replaceChild(v, w);
    assertEquals(w, pe.getChild(0));
  }

  @Test(expected = ChildDoesNotExistException.class)
  public void replaceChildBad1() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    VariableIdentifierExpr w = new VariableIdentifierExpr("v");
    Expr pe = new ParenExpr(v);
    pe.replaceChild(w, v);
  }

  @Test(expected = IllegalArgumentException.class)
  public void replaceChildBad2() throws Exception {
    VariableIdentifierExpr v = new VariableIdentifierExpr("v");
    Expr pe = new ParenExpr(v);
    pe.replaceChild(v, new NullStmt());
  }

}
