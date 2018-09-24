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

import org.junit.Test;

public class VariableIdentifierExprTest {

  @Test
  public void setName() throws Exception {
    VariableIdentifierExpr x = new VariableIdentifierExpr("x");
    assertEquals("x", x.getName());
    x.setName("y");
    assertEquals("y", x.getName());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChild() throws Exception {
    VariableIdentifierExpr x = new VariableIdentifierExpr("x");
    x.getChild(0);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChild() throws Exception {
    VariableIdentifierExpr x = new VariableIdentifierExpr("x");
    x.setChild(0, null);
  }

  @Test
  public void getNumChildren() throws Exception {
    VariableIdentifierExpr x = new VariableIdentifierExpr("x");
    assertEquals(0, x.getNumChildren());
  }

}