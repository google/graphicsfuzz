/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

public class RemoveInitializerMutationFinderTest {

  @Test
  public void testRemoveInitializerMine() throws Exception {
    final String program = "#version 300 es\n"
        + "int var1 = 22;"
        + "mat3 var2 = vec3(3.0,3.0,3.0), var3 = vec3(0,0,0);"
        + "int foo(int x) {"
        + "  const int var1 = 0;"
        + "  int s;"
        + "  float a[3] = float[](1,1,1), z = 1;"
        + "  mat3 matrix1 = vec3(3.0,3.0,3.0), matrix2 = vec3(1.0,1.4,1.1);"
        + "  return x;"
        + "}"
        + "void main () {"
        + "  int a = 0;"
        + "  int b = 0;"
        + "  int c = 0;"
        + "  for(int i = 0; i < 10; i++) {"
        + "    a++;"
        + "    b += 2;"
        + "    c += 3;"
        + "    int u = (i * 3) / i;"
        + "  }"
        + "}";

    final String expected = "#version 300 es\n"
        + "int var1;"
        + "mat3 var2 = vec3(3.0,3.0,3.0), var3;"
        + "int foo(int x) {"
        + "  const int var1 = 0;"
        + "  int s;"
        + "  float a[3], z = 1;"
        + "  mat3 matrix1, matrix2 = vec3(1.0,1.4,1.1);"
        + "  return x;"
        + "}"
        + "void main () {"
        + "  int a;"
        + "  int b;"
        + "  int c = 0;"
        + "  for(int i = 0; i < 10; i++) {"
        + "    a++;"
        + "    b += 2;"
        + "    c += 3;"
        + "    int u;"
        + "  }"
        + "}";

    TranslationUnit tu = ParseHelper.parse(program);

    final List<ReplaceDeclInfoMutation> ops =
        new RemoveInitializerMutationFinder(tu).findMutations();
    assertEquals(11, ops.size());

    ops.get(0).apply();
    ops.get(2).apply();
    ops.get(3).apply();
    ops.get(5).apply();
    ops.get(7).apply();
    ops.get(8).apply();
    ops.get(10).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }
}
