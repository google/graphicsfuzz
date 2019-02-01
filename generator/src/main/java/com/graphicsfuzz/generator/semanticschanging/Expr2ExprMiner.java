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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;

abstract class Expr2ExprMiner extends TransformationMinerBase<Expr2Expr> {

  protected final Typer typer;
  protected final IParentMap parentMap;

  public Expr2ExprMiner(TranslationUnit tu, ShadingLanguageVersion shadingLanguageVersion) {
    super(tu);
    this.typer = new Typer(tu, shadingLanguageVersion);
    this.parentMap = IParentMap.createParentMap(tu);
  }

}
