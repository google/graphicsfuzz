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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AstFuzzerSwizzle extends AstFuzzer {

  public AstFuzzerSwizzle(ShadingLanguageVersion shadingLanguageVersion, IRandom random) {
    super(shadingLanguageVersion, random);
  }

  @Override
  public TranslationUnit generateNewShader(TranslationUnit tu) {

    TranslationUnit result = tu.clone();
    Typer typer = new Typer(result, getShadingLanguageVersion());

    new StandardVisitor() {

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        swizzleExpr(functionCallExpr, typer);
      }

      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (!binaryExpr.getOp().isSideEffecting()) {
          swizzleExpr(binaryExpr, typer);
        }
      }

      @Override
      public void visitTernaryExpr(TernaryExpr ternaryExpr) {
        super.visitTernaryExpr(ternaryExpr);
        swizzleExpr(ternaryExpr, typer);
      }

      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        swizzleExpr(typeConstructorExpr, typer);
      }

    }.visit(result);
    return result;
  }

  private void swizzleExpr(Expr expr, Typer typer) {

    int numChildren = expr.getNumChildren();
    Map<Type, List<Expr>> expressionsByTpe = new HashMap<>();

    // Make mapping from Type to List<Expr>
    for (int i = 0; i < numChildren; i++) {
      Expr child = expr.getChild(i);

      if (typer.lookupType(child) == null) {
        continue;
      }
      if (!expressionsByTpe.containsKey(typer.lookupType(child).getWithoutQualifiers())) {
        expressionsByTpe
              .put(typer.lookupType(child).getWithoutQualifiers(), new ArrayList<>());
      }
      expressionsByTpe.get(typer.lookupType(child).getWithoutQualifiers()).add(child);
    }

    // Take each list of expressions with the same type
    for (Type type : expressionsByTpe.keySet()) {
      List<Expr> sameTypeExpressions = expressionsByTpe.get(type);

      //Take each Expr
      for (int i = 0; i < sameTypeExpressions.size(); i++) {

        int randomIndex = getRandom().nextInt(sameTypeExpressions.size());

        if (i != randomIndex) {
          final Expr oldChild = sameTypeExpressions.get(i);
          final Expr newChild = sameTypeExpressions.get(randomIndex);

          expr.replaceChild(oldChild, newChild);
        }
      }
    }


  }


}
