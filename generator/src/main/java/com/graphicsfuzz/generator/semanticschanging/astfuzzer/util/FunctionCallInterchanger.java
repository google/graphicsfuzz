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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.util;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import java.util.List;

public class FunctionCallInterchanger implements ExprInterchanger {

  private final String name;

  public FunctionCallInterchanger(String name) {
    this.name = name;
  }

  @Override
  public Expr interchangeExpr(List<Expr> args) {
    return new FunctionCallExpr(name, args);
  }

  @Override
  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }
}
