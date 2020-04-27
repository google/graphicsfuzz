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

package com.graphicsfuzz.common.ast.stmt;

import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public final class PragmaStatement extends Declaration {

  public static final PragmaStatement OPTIMIZE_ON = new PragmaStatement();
  public static final PragmaStatement OPTIMIZE_OFF = new PragmaStatement();
  public static final PragmaStatement DEBUG_ON = new PragmaStatement();
  public static final PragmaStatement DEBUG_OFF = new PragmaStatement();
  public static final PragmaStatement INVARIANT_ALL = new PragmaStatement();

  private PragmaStatement() {
    // Enumeration pattern.
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitPragmaStatement(this);
  }

  @Override
  public PragmaStatement clone() {
    return this;
  }

  @Override
  public String getText() {
    String result = "#pragma ";
    if (this == OPTIMIZE_ON) {
      result += "optimize(on)";
    } else if (this == OPTIMIZE_OFF) {
      result += "optimize(off)";
    } else if (this == DEBUG_ON) {
      result += "debug(on)";
    } else if (this == DEBUG_OFF) {
      result += "debug(off)";
    } else {
      assert this == INVARIANT_ALL;
      result += "invariant(all)";
    }
    return result + "\n";
  }

}
