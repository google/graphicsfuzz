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

package com.graphicsfuzz.common.ast.decl;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class FunctionPrototypeTest {

  final String name = "foo";
  final Type returnType = BasicType.INT;
  final ParameterDecl p1 = new ParameterDecl("a", BasicType.FLOAT, null);
  final ParameterDecl p2 = new ParameterDecl("b", BasicType.VEC2, null);
  final ParameterDecl p3 = new ParameterDecl("c", BasicType.VEC3, null);
  final FunctionPrototype proto = new FunctionPrototype(name, returnType, Arrays.asList(p1, p2, p3));

  @Test
  public void testGetters() {
    assertEquals(name, proto.getName());
    assertEquals(returnType, proto.getReturnType());
    assertEquals(p1, proto.getParameter(0));
    assertEquals(p2, proto.getParameter(1));
    assertEquals(p3, proto.getParameter(2));
    assertEquals(3, proto.getNumParameters());
  }

  @Test
  public void removeParameter() {
    proto.removeParameter(1);
    assertEquals(p1, proto.getParameter(0));
    assertEquals(p3, proto.getParameter(1));
    assertEquals(2, proto.getNumParameters());
  }

}
