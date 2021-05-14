/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.ast.decl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class ArrayInfoTest {

  @Test
  public void testClone() {
    final Expr sizeExpr = new IntConstantExpr("1");
    final ArrayInfo arrayInfo1 = new ArrayInfo(Collections.singletonList(Optional.of(sizeExpr)));
    final ArrayInfo arrayInfo2 = arrayInfo1.clone();
    assertNotSame(arrayInfo1, arrayInfo2);
    assertEquals(arrayInfo1.getDimensionality(), arrayInfo2.getDimensionality());
    assertEquals(1, arrayInfo2.getDimensionality());
    assertSame(sizeExpr, arrayInfo1.getSizeExpr(0));
    assertNotSame(sizeExpr, arrayInfo2.getSizeExpr(0));
    assertEquals(sizeExpr.getText(), arrayInfo2.getSizeExpr(0).getText());
  }

}
