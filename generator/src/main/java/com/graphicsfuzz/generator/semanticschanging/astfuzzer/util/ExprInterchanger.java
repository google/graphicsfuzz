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
import java.util.List;

/**
 * The motivation for this interface is to provide a means for converting between expressions that
 * have the same signature, but that are represented by different AST nodes.
 *
 * <p>For example, the + operator and the min function can both be applied to float arguments,
 * in each case returning a float, but + is represented by a binary expression node, while min
 * is represented by a function call.
 */
public interface ExprInterchanger {

  /** .
   * @param args The arguments to the source expression.  For example, if the source expression was
   *        1 + 2, args would be [ 1, 2 ].
   * @return The interchanged expression, which will have the same arguments as the original. For
   *         example, if the source was 1 + 2 and the resulting expression is a call to "min",
   *         the result will be min(1, 2).
   */
  public Expr interchangeExpr(List<Expr> args);

}
