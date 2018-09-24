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

public enum UnOp implements Op {
  POST_INC, POST_DEC, PRE_INC, PRE_DEC, PLUS, MINUS, BNEG, LNOT;

  /**
   * Produce text representation of the unary operator.
   * @return Text representation
   */
  @Override
  public String getText() {
    switch (this) {
      case BNEG:
        return "~";
      case LNOT:
        return "!";
      case MINUS:
        return "-";
      case PLUS:
        return "+";
      case POST_DEC:
        return "--";
      case POST_INC:
        return "++";
      case PRE_DEC:
        return "--";
      default:
      assert this == PRE_INC : "Unknown unary operator: " + this;
        return "++";
    }
  }

  @Override
  public boolean isSideEffecting() {
    return this == PRE_INC || this == PRE_DEC || this == POST_INC || this == POST_DEC;
  }

}
