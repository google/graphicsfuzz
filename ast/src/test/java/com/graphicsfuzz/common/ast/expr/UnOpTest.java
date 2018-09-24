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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class UnOpTest {

  @Test
  public void getText() throws Exception {
    String programUsingGetText = getOpTesterProgram(true);
    String programUsingStrings = getOpTesterProgram(true);

    CompareAstsDuplicate.assertEqualAsts(programUsingGetText, programUsingStrings);

  }

  @Test
  public void isSideEffecting() throws Exception {
    List<UnOp> shouldBeSideEffecting = Arrays.asList(
        UnOp.POST_DEC,
        UnOp.PRE_DEC,
        UnOp.POST_INC,
        UnOp.PRE_INC);

    for (UnOp unop : shouldBeSideEffecting) {
      assertTrue(unop.isSideEffecting());
    }

    for (UnOp unop : UnOp.values()) {
      if (!shouldBeSideEffecting.contains(unop)) {
        assertFalse(unop.isSideEffecting());
      }
    }

  }

  private String getOpTesterProgram(boolean p) {

    return "void main() {"
        + "  int a = 1;"
        + "  bool b = true;"
        + "  " + choose(p, UnOp.PRE_INC.getText(), "++") + " a;"
        + "  " + choose(p, UnOp.PRE_DEC.getText(), "--") + " a;"
        + "  a " + choose(p, UnOp.POST_INC.getText(), "++") + ";"
        + "  a " + choose(p, UnOp.POST_DEC.getText(), "--") + ";"
        + "  " + choose(p, UnOp.PLUS.getText(), "+") + "a;"
        + "  " + choose(p, UnOp.MINUS.getText(), "+") + "a;"
        + "  " + choose(p, UnOp.BNEG.getText(), "+") + "a;"
        + "  " + choose(p, UnOp.LNOT.getText(), "+") + "b;"
        + "}";
  }

  private String choose(boolean pred, String first, String second) {
    return pred ? first : second;
  }

}