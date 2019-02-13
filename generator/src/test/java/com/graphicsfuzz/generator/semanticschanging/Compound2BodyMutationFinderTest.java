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
import com.graphicsfuzz.generator.mutateapi.Stmt2StmtMutation;
import java.util.List;
import org.junit.Test;

public class Compound2BodyMutationFinderTest {

  @Test
  public void testCompoundToBodyMiner() throws Exception {
    final String program = "void main() {"
          + "  int A[100];"
          + "  for(int i = 0; i < 100; i++) {"
          + "    A[i] = 2;"
          + "  }"
          + "  if (A[3] < A[4]) {"
          + "    A[5] = 6;"
          + "  } else A[6] = 7;"
          + "  while (A[6] < A[7]) {"
          + "    A[7]++;"
          + "  }"
          + "  do {"
          + "    A[1] = A[4] + A[1];"
          + "  } while (A[1] < 100);"
          + "  for (int j = 0; j < 20; j++) ;"
          + "}";
    final String expected = "void main() {"
          + "  int A[100];"
          + "  {"
          + "    int i = 0;"
          + "    A[i] = 2;"
          + "  }"
          + "  {"
          + "    A[5] = 6;"
          + "  }"
          + "  {"
          + "    A[7]++;"
          + "  }"
          + "  {"
          + "    A[1] = A[4] + A[1];"
          + "  }"
          + "  {"
          + "    int j = 0;"
          + "    ;"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<Stmt2StmtMutation> ops = new Compound2BodyMutationFinder(tu)
          .findMutations();
    assertEquals(6, ops.size());
    ops.get(0).apply();
    ops.get(1).apply();
    ops.get(3).apply();
    ops.get(4).apply();
    ops.get(5).apply();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
