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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutation;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutationFinder;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Expr2ArrayAccessMutationFinder extends Expr2ExprMutationFinder {

  private final SortedMap<String, Type> globalArrays;
  private final IRandom generator;

  public Expr2ArrayAccessMutationFinder(TranslationUnit tu, IRandom generator) {
    super(tu);
    this.generator = generator;
    this.globalArrays = new TreeMap<>(String::compareTo);
  }

  @Override
  protected void visitVariableDeclInfoAfterAddedToScope(VariableDeclInfo declInfo) {
    if (!atGlobalScope()) {
      return;
    }
    ScopeEntry se = currentScope.lookupScopeEntry(declInfo.getName());
    if (se.getType() instanceof ArrayType) {
      globalArrays.put(declInfo.getName(), ((ArrayType) se.getType()).getBaseType()
          .getWithoutQualifiers());
    }
  }

  @Override
  protected void visitExpr(Expr expr) {
    if (underForLoopHeader) {
      return;
    }
    super.visitExpr(expr);
    if (expr instanceof ConstantExpr) {
      // Too many cases where this will not work
      return;
    }
    final Type type = typer.lookupType(expr);
    if (type == null) {
      return;
    }
    final Type unqualifiedType = type.getWithoutQualifiers();
    if (unqualifiedType != BasicType.FLOAT && unqualifiedType != BasicType.INT) {
      return;
    }
    final List<String> arrays = globalArrays.keySet().stream().filter(item ->
        globalArrays.get(item).equals(unqualifiedType))
          .collect(Collectors.toList());
    if (arrays.isEmpty()) {
      return;
    }
    final String arrayName = arrays.get(generator.nextInt(arrays.size()));
    Expr index = expr;
    if (unqualifiedType == BasicType.FLOAT) {
      index = new TypeConstructorExpr("int", Arrays.asList(expr));
    }
    ArrayIndexExpr arrayIndexExpr = new ArrayIndexExpr(new VariableIdentifierExpr(arrayName),
          index);
    addMutation(new Expr2ExprMutation(parentMap.getParent(expr), expr,
        () -> arrayIndexExpr));
  }

}
