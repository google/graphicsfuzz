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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;

public class Expr2LiteralMiner extends Expr2ExprMiner {

  private final IRandom generator;

  public Expr2LiteralMiner(TranslationUnit tu, ShadingLanguageVersion shadingLanguageVersion,
      IRandom generator) {
    super(tu, shadingLanguageVersion);
    this.generator = generator;
  }

  @Override
  protected void visitExpr(Expr expr) {
    if (underForLoopHeader) {
      return;
    }
    super.visitExpr(expr);
    if (typer.lookupType(expr) != null) {
      new LiteralFuzzer(generator).fuzz(typer.lookupType(expr).getWithoutQualifiers())
            .ifPresent(item -> addTransformation(
                  new Expr2Expr(parentMap.getParent(expr), expr, item)));
    }
  }

}
