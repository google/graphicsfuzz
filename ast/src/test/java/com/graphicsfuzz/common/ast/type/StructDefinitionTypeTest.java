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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.typing.Scope;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class StructDefinitionTypeTest {

  @Test
  public void insertField() throws Exception {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("temp"),
        Arrays.asList("a", "b"),
        Arrays.asList(BasicType.FLOAT, BasicType.FLOAT));
    t.insertField(2, "c", BasicType.FLOAT);
    assertEquals(t.getNumFields(), 3);
    assertEquals("c", t.getFieldNames().get(2));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void insertField2() throws Exception {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("temp"),
        Arrays.asList("a", "b"),
        Arrays.asList(BasicType.FLOAT, BasicType.FLOAT));
    t.insertField(4, "d", BasicType.FLOAT);
  }

  @Test(expected = IllegalArgumentException.class)
  public void removeField() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    t.removeField("z"); // Should throw exception
  }

  @Test
  public void removeField2() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    assertEquals(2, t.getNumFields());
    t.removeField("x");
    assertEquals(1, t.getNumFields());
    assertEquals("y", t.getFieldName(0));
    assertEquals(BasicType.IVEC3, t.getFieldType("y"));
  }

  @Test
  public void getFieldIndex() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    assertEquals(0, t.getFieldIndex("x"));
    assertEquals(1, t.getFieldIndex("y"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFieldIndex2() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    t.getFieldIndex("z");
  }

  @Test
  public void getFieldTypes() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT2X2, BasicType.MAT3X3));
    assertEquals(t.getFieldTypes().get(0), BasicType.MAT2X2);
    assertEquals(t.getFieldTypes().get(1), BasicType.MAT3X3);
  }

  @Test
  public void hasCanonicalConstant() {
    StructDefinitionType t = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    assertTrue(t.hasCanonicalConstant(new Scope()));
    assertEquals("astruct(mat4(1.0), vec4(1.0))",
        t.getCanonicalConstant(new Scope()).getText());
  }

  @Test
  public void testEquals() {
    final StructDefinitionType t1 = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    final StructDefinitionType t2 = new StructDefinitionType(new StructNameType("astruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    final StructDefinitionType t3 = new StructDefinitionType(new StructNameType("anotherstruct"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    final StructDefinitionType t4 = new StructDefinitionType(Optional.empty(),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    final StructDefinitionType t5 = new StructDefinitionType(Optional.empty(),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    final StructDefinitionType t6 = new StructDefinitionType(Optional.empty(),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT3X4, BasicType.VEC4));

    assertEquals(t1, t2);
    assertEquals(t1.hashCode(), t2.hashCode());
    assertEquals(t4, t5);
    assertEquals(t4.hashCode(), t5.hashCode());
    assertNotEquals(t1, t3);
    assertNotEquals(t1, t4);
    assertNotEquals(t5, t6);

  }

}
