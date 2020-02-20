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
import java.util.List;
import java.util.Optional;

public abstract class Type implements IAstNode {

  @Override
  public abstract Type clone();

  public abstract boolean hasCanonicalConstant(Optional<Scope> scope);

  public final boolean hasCanonicalConstant() {
    return hasCanonicalConstant(Optional.empty());
  }

  public abstract Expr getCanonicalConstant(Optional<Scope> scope);

  public final Expr getCanonicalConstant() {
    return getCanonicalConstant(Optional.empty());
  }

  public abstract Type getWithoutQualifiers();

  public abstract boolean hasQualifier(TypeQualifier qualifier);

}
