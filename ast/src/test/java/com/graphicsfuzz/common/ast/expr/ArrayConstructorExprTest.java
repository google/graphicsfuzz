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

import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class ArrayConstructorExprTest {

  private ArrayConstructorExpr arrayConstructor;

  @Before
  public void setup() {
    TypeConstructorExpr temp = new TypeConstructorExpr(
        "vec4", new FloatConstantExpr("0.0"));
    arrayConstructor = new ArrayConstructorExpr(new ArrayType(
        BasicType.VEC4,
        new ArrayInfo(3)),
        Arrays.asList(temp.clone(), temp.clone(), temp.clone())
    );
  }

  @Test
  public void getArrayType() throws Exception {
    assertEquals(BasicType.VEC4, arrayConstructor.getArrayType().getBaseType());
  }

  @Test
  public void getArgs() throws Exception {
    assertEquals(3, arrayConstructor.getArgs().size());
    for (Expr arg : arrayConstructor.getArgs()) {
      assertEquals("vec4(0.0)", arg.getText());
    }
  }

  @Test
  public void testClone() throws Exception {
    ArrayConstructorExpr theClone = arrayConstructor.clone();
    assertFalse(arrayConstructor == theClone);
    assertEquals(theClone.getText(), arrayConstructor.getText());
  }

  @Test
  public void getAndSetChild() throws Exception {
    for (int i = 0; i < arrayConstructor.getNumChildren(); i++) {
      assertEquals("vec4(0.0)", arrayConstructor.getChild(i).getText());
    }
    arrayConstructor.setChild(1, new VariableIdentifierExpr("x"));
    arrayConstructor.setChild(2, new VariableIdentifierExpr("y"));
    assertEquals("vec4(0.0)", arrayConstructor.getChild(0).getText());
    assertEquals("x", arrayConstructor.getChild(1).getText());
    assertEquals("y", arrayConstructor.getChild(2).getText());
  }

  @Test
  public void getNumChildren() throws Exception {
    assertEquals(3, arrayConstructor.getNumChildren());
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void getChildBad() {
    arrayConstructor.getChild(-1);
  }

  @Test(expected = IndexOutOfBoundsException.class)
  public void setChildBad() {
    arrayConstructor.setChild(3, new VariableIdentifierExpr("z"));
  }

}