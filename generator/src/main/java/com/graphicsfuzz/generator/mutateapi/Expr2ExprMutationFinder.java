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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.typing.Typer;

public abstract class Expr2ExprMutationFinder extends MutationFinderBase<Expr2ExprMutation> {

  protected final Typer typer;
  protected final IParentMap parentMap;

  public Expr2ExprMutationFinder(TranslationUnit tu) {
    super(tu);
    this.typer = new Typer(tu);
    this.parentMap = IParentMap.createParentMap(tu);
  }

  @Override
  public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
    // Do nothing: we don't want to mutate the expressions associated with case labels.
  }

}
