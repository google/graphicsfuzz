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

package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.typing.Scope;

public abstract class Type implements IAstNode {

  @Override
  public abstract Type clone();

  /**
   * Determines whether a canonical constant exists for the type.
   * @param scope Used to provide details of struct types.
   * @return true if and only if the type has a canonical constant.
   */
  public abstract boolean hasCanonicalConstant(Scope scope);

  /**
   * Requires that hasCanonicalConstant(scope) holds.  Returns the canonical constant for this type.
   * @param scope Used to provide details of struct types.
   * @return A constant expression of this type.
   */
  public abstract Expr getCanonicalConstant(Scope scope);

  /**
   * Yields an unqualified version of the type.
   * @return A type identical to the original type, but with no qualifiers (which will be the
   *         original type if it was already unqualified.
   */
  public abstract Type getWithoutQualifiers();

  /**
   * Returns true if and only if this is a qualified type that has the given qualifier.
   * @param qualifier A qualifier to be tested for.
   * @return true if and only if the type has the given qualifier.
   */
  public abstract boolean hasQualifier(TypeQualifier qualifier);

}
