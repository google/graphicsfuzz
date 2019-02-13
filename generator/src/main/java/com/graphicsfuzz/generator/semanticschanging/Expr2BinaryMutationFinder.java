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
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutation;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutationFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds mutations such as: e -> (e) * (e).
 */
public class Expr2BinaryMutationFinder extends Expr2ExprMutationFinder {

  private final IRandom generator;

  public Expr2BinaryMutationFinder(TranslationUnit tu,
                                   IRandom generator) {
    super(tu);
    this.generator = generator;
  }

  @Override
  protected void visitExpr(Expr expr) {

    if (underForLoopHeader) {
      return;
    }

    final Type exprType = typer.lookupType(expr);

    if (exprType == null) {
      return;
    }

    Optional<BinOp> operator = chooseBinOp(exprType.getWithoutQualifiers());

    if (!operator.isPresent()) {
      return;
    }

    addMutation(new Expr2ExprMutation(parentMap.getParent(expr),
        expr, () -> new BinaryExpr(new ParenExpr(expr), new ParenExpr(expr.clone()),
        operator.get())));
  }

  private Optional<BinOp> chooseBinOp(Type type) {
    final List<BinOp> candidates = new ArrayList<>();

    if (BasicType.allNumericTypesExceptNonSquareMatrices()
          .contains(type)) {
      candidates.add(BinOp.ADD);
      candidates.add(BinOp.DIV);
      candidates.add(BinOp.MUL);
      candidates.add(BinOp.SUB);
    }

    return candidates.isEmpty()
          ? Optional.empty() : Optional.of(candidates.get(generator.nextInt(candidates.size())));
  }

}
