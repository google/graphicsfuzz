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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.IOException;
import org.junit.Test;

public class TruncateLoopsTest {

  private static final String[] CONDITIONS = {"x < -(-20)", "20 > x", "x <= 20", "20 >= x",
      "x > -2", "-2 < x","x >= -2", "-2 <= x"};
  private static final String[] INCREMETS = {"x++", "++x", "x += 1", "x += -(-1)", "x += 5",
      "x--", "--x", "x -= 1", "x -= 5"};
  private static final String[] INIT_CONSTS = {"x = -1", "x = 0", "int x = 0", "x = -(+(-10))",
      "int x = 10"};

  @Test
  public void intConstantsTest() throws Exception {
    final String programBody =
        "void main() {"
            + "int u = 10;"
            + "int x;"
            + "  for($INIT; $COND; $INCREMENT) {"
            + "    u = u * 2;"
            + "  }"
            + "}";

    for (int cond_index = 0; cond_index < CONDITIONS.length; ++cond_index) {
      for (int incr_index = 0; incr_index < INCREMETS.length; ++incr_index) {
        for (int init_index = 0; init_index < INIT_CONSTS.length; ++init_index) {
          final boolean isSane = (cond_index < 4 && incr_index < 5)
              || (4 <= cond_index && 5 <= incr_index);
          testProgram(programBody
                  .replace("$INIT", INIT_CONSTS[init_index])
                  .replace("$COND", CONDITIONS[cond_index])
                  .replace("$INCREMENT", INCREMETS[incr_index]),
              isSane);

        }
      }
    }
  }

  private void testProgram(String program, boolean isSane) throws IOException,
      ParseTimeoutException, InterruptedException, GlslParserException {
    TranslationUnit tu =  ParseHelper.parse(program);
    new TruncateLoops(30, "webGL_", tu, true);
    if(isSane) {
      CompareAstsDuplicate.assertEqualAsts(program, tu);
    } else {
      assertProgramsNotEqual(program, tu);
    }
    tu =  ParseHelper.parse(program);
    new TruncateLoops(0, "webGL_", tu, true);
    assertProgramsNotEqual(program, tu);
  }

  private void assertProgramsNotEqual(String program, TranslationUnit otherProgram)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    assert !PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program))
        .equals(PrettyPrinterVisitor.prettyPrintAsString(otherProgram));
  }

}
