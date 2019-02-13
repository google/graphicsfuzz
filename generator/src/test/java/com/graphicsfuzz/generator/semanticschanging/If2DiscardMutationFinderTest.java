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

public class If2DiscardMutationFinderTest {

  @Test
  public void testOpportunities() throws Exception {

    final String program = "#version 100\n"
        + "void main() {"
        + "  if (true) {"
        + "  } else {"
        + "    if (false) {"
        + "    }"
        + "  }"
        + "}";
    final String expected1 = "#version 100\n"
        + "void main() {"
        + "  if (true) {"
        + "  } else {"
        + "    if (false) {"
        + "    } else discard;"
        + "  }"
        + "}";
    final String expected2 = "#version 100\n"
        + "void main() {"
        + "  if (true) discard;"
        + "  else {"
        + "    if (false) {"
        + "    }"
        + "  }"
        + "}";

    TranslationUnit tu = ParseHelper.parse(program);
    List<Stmt2StmtMutation> ops = new If2DiscardMutationFinder(tu).findMutations();
    assertEquals(4, ops.size());
    ops.get(1).apply();
    CompareAsts.assertEqualAsts(expected1, tu);
    tu = ParseHelper.parse(program);
    ops = new If2DiscardMutationFinder(tu).findMutations();
    assertEquals(4, ops.size());
    ops.get(2).apply();
    CompareAsts.assertEqualAsts(expected2, tu);
  }

}
