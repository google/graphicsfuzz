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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class UIntConstantExpr extends ConstantExpr {

  private String value;

  public UIntConstantExpr(String text) {
    assert text.endsWith("u");
    this.value = text;
  }

  @Override
  public boolean hasChild(IAstNode child) {
    return false;
  }

  public String getValue() {
    return value;
  }

  public int getNumericValue() {
    if (isOctal()) {
      return Integer.parseInt(getValueWithoutSuffix(), 8);
    }
    if (isHex()) {
      return Integer.parseInt(getValueWithoutSuffix().substring("0x".length()), 16);
    }
    return Integer.parseInt(getValueWithoutSuffix());
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitUIntConstantExpr(this);
  }

  @Override
  public UIntConstantExpr clone() {
    return new UIntConstantExpr(value);
  }

  private String getValueWithoutSuffix() {
    assert value.endsWith("u");
    return value.substring(0, value.length() - 1);
  }

  private boolean isOctal() {
    return getValueWithoutSuffix().startsWith("0") && getValueWithoutSuffix().length() > 1
        && !isHex();
  }

  private boolean isHex() {
    return getValueWithoutSuffix().startsWith("0x");
  }

}
