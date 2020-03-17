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

import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BinaryExprTemplate extends AbstractExprTemplate {

  private final List<? extends Type> lhsTypes;
  private final Type rhsType;
  private final Type resultType;
  private final BinOp op;

  public BinaryExprTemplate(List<? extends Type> lhsTypes, Type rhsType, Type resultType,
      BinOp op) {
    this.lhsTypes = lhsTypes;
    this.rhsType = rhsType;
    this.resultType = resultType;
    this.op = op;
  }

  public BinaryExprTemplate(Type lhsType, Type rhsType, Type resultType, BinOp op) {
    this(Collections.singletonList(lhsType), rhsType, resultType, op);
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new ParenExpr(new BinaryExpr(args[0], args[1], op));
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<Type>> getArgumentTypes() {
    return Arrays.asList(new ArrayList<>(lhsTypes),
        Collections.singletonList(rhsType));
  }

  @Override
  public int getNumArguments() {
    return 2;
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0 || index == 1;
    if (index == 1) {
      return false;
    }
    return isLValue();
  }

  @Override
  public boolean isLValue() {
    switch (op) {
      case ADD_ASSIGN:
      case BAND_ASSIGN:
      case BOR_ASSIGN:
      case BXOR_ASSIGN:
      case DIV_ASSIGN:
      case MOD_ASSIGN:
      case MUL_ASSIGN:
      case SHL_ASSIGN:
      case SHR_ASSIGN:
      case SUB_ASSIGN:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean isConst() {
    switch (op) {
      case ADD_ASSIGN:
      case BAND_ASSIGN:
      case BOR_ASSIGN:
      case BXOR_ASSIGN:
      case DIV_ASSIGN:
      case MOD_ASSIGN:
      case MUL_ASSIGN:
      case SHL_ASSIGN:
      case SHR_ASSIGN:
      case SUB_ASSIGN:
      case COMMA:
        return false;
      default:
        return true;
    }
  }

  @Override
  protected String getTemplateName() {
    return "BINARY(" + op + ")";
  }

}
