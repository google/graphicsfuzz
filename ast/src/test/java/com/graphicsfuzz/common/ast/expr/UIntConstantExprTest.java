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

import org.junit.Test;

public class UIntConstantExprTest {

  @Test
  public void getValue() throws Exception {
    UIntConstantExpr uice = new UIntConstantExpr("3u");
    assertEquals("3u", uice.getValue());
  }

  @Test
  public void accept() throws Exception {
    assertEquals("4u", new UIntConstantExpr("4u").getText());
  }

  @Test
  public void testClone() throws Exception {
    UIntConstantExpr uice = new UIntConstantExpr("3u");
    UIntConstantExpr uice2 = uice.clone();
    assertFalse(uice == uice2);
    assertEquals(uice.getValue(), uice2.getValue());
  }

}