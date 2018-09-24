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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import org.junit.Test;

public class StructTypeTest {

  @Test
  public void testEquals() throws Exception {

    StructType t1 = makeStruct1();
    StructType t2 = makeStruct2();
    assertEquals(t1, t2);

    StructType t3 = new StructType("nested",
        Arrays.asList("a"), Arrays.asList(t1));

    StructType t4 = new StructType("nested",
        Arrays.asList("a"), Arrays.asList(t2));
    assertEquals(t3, t4);

    t1.insertField(0, "new", BasicType.FLOAT);
    assertNotEquals(t1, t2);
    assertNotEquals(t3, t4);

  }

  private StructType makeStruct1() {
    ArrayList<Type> arrayList = new ArrayList<>();
    arrayList.addAll(Arrays.asList(BasicType.FLOAT, BasicType.BOOL, BasicType.MAT3X2));
    return new StructType("A", Arrays.asList("x", "y", "z"), arrayList);
  }

  private StructType makeStruct2() {
    LinkedList<Type> linkedList = new LinkedList<>();
    linkedList.addAll(Arrays.asList(BasicType.FLOAT, BasicType.BOOL, BasicType.MAT3X2));
    return new StructType("A", Arrays.asList("x", "y", "z"), linkedList);
  }

  @Test
  public void testHashCode() throws Exception {
    StructType t1 = makeStruct1();
    StructType t2 = makeStruct2();
    assertEquals(t1.hashCode(), t2.hashCode());

    StructType t3 = new StructType("nested",
        Arrays.asList("a"), Arrays.asList(t1));

    StructType t4 = new StructType("nested",
        Arrays.asList("a"), Arrays.asList(t2));
    assertEquals(t3, t4);
    assertEquals(t3.hashCode(), t3.hashCode());
  }

  @Test
  public void insertField() throws Exception {
    StructType t = new StructType("temp", Arrays.asList("a", "b"),
        Arrays.asList(BasicType.FLOAT, BasicType.FLOAT));
    t.insertField(2, "c", BasicType.FLOAT);
    assertEquals(t.getNumFields(), 3);
    assertEquals("c", t.getFieldNames().get(2));
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void insertField2() throws Exception {
    StructType t = new StructType("temp", Arrays.asList("a", "b"),
        Arrays.asList(BasicType.FLOAT, BasicType.FLOAT));
    t.insertField(4, "d", BasicType.FLOAT);
  }

  @Test(expected = IllegalArgumentException.class)
  public void removeField() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    t.removeField("z"); // Should throw exception
  }

  @Test
  public void removeField2() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    assertEquals(2, t.getNumFields());
    t.removeField("x");
    assertEquals(1, t.getNumFields());
    assertEquals("y", t.getFieldName(0));
    assertEquals(BasicType.IVEC3, t.getFieldType("y"));
  }

  @Test
  public void getFieldIndex() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    assertEquals(0, t.getFieldIndex("x"));
    assertEquals(1, t.getFieldIndex("y"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFieldIndex2() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC4, BasicType.IVEC3));
    t.getFieldIndex("z");
  }

  @Test
  public void getFieldTypes() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT2X2, BasicType.MAT3X3));
    assertEquals(t.getFieldTypes().get(0), BasicType.MAT2X2);
    assertEquals(t.getFieldTypes().get(1), BasicType.MAT3X3);
  }

  @Test
  public void hasCanonicalConstant() {
    StructType t = new StructType("astruct", Arrays.asList("x", "y"),
        Arrays.asList(BasicType.MAT4X4, BasicType.VEC4));
    assertTrue(t.hasCanonicalConstant());
    assertEquals("astruct(mat4(1.0), vec4(1.0))",
        t.getCanonicalConstant().getText());
  }

}