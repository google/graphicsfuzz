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

package com.graphicsfuzz.generator.fuzzer.templates;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.List;

public interface IExprTemplate {

  Expr generateExpr(IRandom generator, Expr... args);

  default Expr generateExpr(IRandom generator, List<Expr> args) {
    Expr[] temp = new Expr[args.size()];
    return generateExpr(generator, args.toArray(temp));
  }

  Type getResultType();

  List<List<? extends Type>> getArgumentTypes();

  boolean requiresLValueForArgument(int index);

  boolean isLValue();

  boolean isConst();

  int getNumArguments();

}
