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

public class RemoveStmtMutationFinderTest {

  @Test
  public void testRemoveStmtMiner() throws Exception {
    final String program = "#version 300 es\n"
        + "void main() {"
          + "  int a;"
          + "  a = 2;"
          + "  int b, c = 3;"
          + "  if(a < c) {"
          + "    a = 1;"
          + "    b = a + 1;"
          + "  } else {"
          + "    b = c + 2;"
          + "  }"
          + "}";
    final String expected = "#version 300 es\n"
        + "void main() {"
          + "  int a;"
          + "  int b, c = 3;"
          + "  if(a < c) {"
          + "    a = 1;"
          + "    b = a + 1;"
          + "  } else {"
          + "    b = c + 2;"
          + "  }"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final List<RemoveStmtMutation> ops = new RemoveStmtMutationFinder(tu)
          .findMutations();
    assertEquals(5, ops.size());
    ops.get(0).apply();
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
