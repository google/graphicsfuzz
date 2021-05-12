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

package com.graphicsfuzz.common.ast.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class ArrayTypeTest {

  @Test
  public void testEquals() {
    final ArrayType intArrayNoSize = new ArrayType(BasicType.INT,
        new ArrayInfo(Collections.singletonList(Optional.empty())));
    final ArrayType anotherIntArrayNoSize = new ArrayType(BasicType.INT,
        new ArrayInfo(Collections.singletonList(Optional.empty())));

    final ArrayType floatArrayNoSize = new ArrayType(BasicType.FLOAT,
        new ArrayInfo(Collections.singletonList(Optional.empty())));
    final ArrayType anotherFloatArrayNoSize = new ArrayType(BasicType.FLOAT,
        new ArrayInfo(Collections.singletonList(Optional.empty())));

    final ArrayType intArraySize2 = new ArrayType(BasicType.INT,
        new ArrayInfo(Collections.singletonList(Optional.of(new BinaryExpr(
            new IntConstantExpr("1"), new IntConstantExpr("1"),
            BinOp.ADD)))));
    intArraySize2.getArrayInfo().setConstantSizeExpr(0, 2);

    final ArrayType intArraySize3 = new ArrayType(BasicType.INT,
        new ArrayInfo(Collections.singletonList(Optional.of(new BinaryExpr(
            new IntConstantExpr("2"), new IntConstantExpr("1"),
            BinOp.ADD)))));
    intArraySize3.getArrayInfo().setConstantSizeExpr(0, 3);

    final ArrayType anotherIntArraySize2 = new ArrayType(BasicType.INT,
        new ArrayInfo(Collections.singletonList(Optional.of(new IntConstantExpr("2")))));
    anotherIntArraySize2.getArrayInfo().setConstantSizeExpr(0, 2);

    assertEquals(intArrayNoSize, anotherIntArrayNoSize);
    assertEquals(intArrayNoSize.hashCode(), anotherIntArrayNoSize.hashCode());
    assertNotEquals(intArrayNoSize, intArraySize2);
    assertEquals(floatArrayNoSize, anotherFloatArrayNoSize);
    assertEquals(floatArrayNoSize.hashCode(), anotherFloatArrayNoSize.hashCode());
    assertNotEquals(intArrayNoSize, floatArrayNoSize);
    assertEquals(intArraySize2, anotherIntArraySize2);
    assertEquals(intArraySize2.hashCode(), anotherIntArraySize2.hashCode());
    assertNotEquals(intArraySize2, intArraySize3);

  }

}
