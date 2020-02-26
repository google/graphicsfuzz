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

package com.graphicsfuzz.common.ast.type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.typing.Scope;
import org.junit.Test;

public class BasicTypeTest {

  @Test
  public void testCanonicalConstant() {
    assertEquals("1", BasicType.INT.getCanonicalConstant(new Scope()).getText());
    assertEquals("1u", BasicType.UINT.getCanonicalConstant(new Scope()).getText());
    assertEquals("1.0", BasicType.FLOAT.getCanonicalConstant(new Scope()).getText());
    assertEquals("true", BasicType.BOOL.getCanonicalConstant(new Scope()).getText());
    assertEquals("uvec4(1u)", BasicType.UVEC4.getCanonicalConstant(new Scope()).getText());
    assertEquals("mat2(1.0)", BasicType.MAT2X2.getCanonicalConstant(new Scope()).getText());
  }

  @Test
  public void testNumericTypes() {
    assertTrue(BasicType.allNumericTypes().contains(BasicType.IVEC2));
    assertFalse(BasicType.allNumericTypes().contains(BasicType.BOOL));
    assertFalse(BasicType.allNumericTypes().contains(BasicType.BVEC2));
    assertFalse(BasicType.allNumericTypes().contains(BasicType.BVEC3));
    assertFalse(BasicType.allNumericTypes().contains(BasicType.BVEC4));
  }

}
