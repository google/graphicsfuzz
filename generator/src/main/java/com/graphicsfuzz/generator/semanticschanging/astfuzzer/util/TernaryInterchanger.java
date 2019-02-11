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

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import java.util.List;

public class TernaryInterchanger implements ExprInterchanger {

  public TernaryInterchanger() {
  }

  @Override
  public Expr interchangeExpr(List<Expr> args) {

    assert (args.size() >= 1);
    Expr arg0 = args.get(0);
    Expr arg1 = args.size() > 1 ? args.get(1) : args.get(0);

    // TODO: For now, we always use "true" as the ternary condition; this could be randomized.
    return new ParenExpr(new TernaryExpr(
        BoolConstantExpr.TRUE,
        arg0,
        arg1));
  }

}
