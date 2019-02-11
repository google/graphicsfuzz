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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.List;
import org.junit.Test;

public class Expr2BinaryMutationFinderTest {

  @Test
  public void testExpr2BinaryMiner() throws Exception {
    final String program = "#version 100\n"
        + "int foo(int x) {"
        + "  return x;"
        + "}"
        + "void main () {"
        + "  vec2 v1 = vec2(0.0, 0.0);"
        + "  vec2 v2 = vec2(0.0, 0.0);"
        + "  v1 = v1 - v2;"
        + "  int cnt = 0, z;"
        + "  for(int i = 0; i < 100; i++) {"
        + "    cnt++;"
        + "    z += 2 + foo(5);"
        + "  }"
        + "}";

    final String expected = "#version 100\n"
        + "int foo(int x) {"
        + "  return (x) * (x);"
        + "}"
        + "void main () {"
        + "  vec2 v1 = vec2(0.0, 0.0);"
        + "  vec2 v2 = (vec2(0.0, 0.0)) * (vec2(0.0, 0.0));"
        + "  v1 = (v1 - v2) * (v1 - v2);"
        + "  int cnt = 0, z;"
        + "  for(int i = 0; i < 100; i++) {"
        + "    cnt++;"
        + "    (z += (2 + foo(5)) * (2 + foo(5))) * (z += 2 + foo(5));"
        + "  }"
        + "}";


    TranslationUnit tu = ParseHelper.parse(program);

    final List<Expr2ExprMutation> ops = new Expr2BinaryMutationFinder(tu,
        getGenerator()).findMutations();

    ops.get(0).apply();
    ops.get(6).apply();
    ops.get(10).apply();
    ops.get(19).apply();
    ops.get(20).apply();

    CompareAsts.assertEqualAsts(expected, tu);
  }

  private IRandom getGenerator() {
    return new IRandom() {
      @Override
      public int nextInt(int bound) {
        return 2;
      }

      @Override
      public Float nextFloat() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean nextBoolean() {
        throw new UnsupportedOperationException();
      }

      @Override
      public IRandom spawnChild() {
        throw new UnsupportedOperationException();
      }
    };
  }

}
