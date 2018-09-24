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
import java.util.List;

public class BinaryExprTemplate extends AbstractExprTemplate {

  private List<? extends Type> lhsTypes;
  private List<? extends Type> rhsTypes;
  private Type resultType;
  private BinOp op;

  public BinaryExprTemplate(List<? extends Type> lhsTypes, Type rhsType, Type resultType,
      BinOp op) {
    this.lhsTypes = lhsTypes;
    this.rhsTypes = new ArrayList<>(Arrays.asList(rhsType));
    this.resultType = resultType;
    this.op = op;
  }

  public BinaryExprTemplate(Type lhsType, Type rhsType, Type resultType, BinOp op) {
    this(new ArrayList<>(Arrays.asList(lhsType)), rhsType, resultType, op);
    String opString = null;
    switch (op) {
      case ADD:
        opString = "+";
        break;
      case ADD_ASSIGN:
        opString = "+=";
        break;
      case ASSIGN:
        opString = "=";
        break;
      case BAND:
        opString = "&";
        break;
      case BAND_ASSIGN:
        opString = "&=";
        break;
      case BOR:
        opString = "|";
        break;
      case BOR_ASSIGN:
        opString = "|=";
        break;
      case BXOR:
        opString = "^";
        break;
      case BXOR_ASSIGN:
        opString = "^=";
        break;
      case COMMA:
        opString = ",";
        break;
      case DIV:
        opString = "/";
        break;
      case DIV_ASSIGN:
        opString = "/=";
        break;
      case EQ:
        opString = "==";
        break;
      case GE:
        opString = ">=";
        break;
      case GT:
        opString = ">";
        break;
      case LAND:
        opString = "&&";
        break;
      case LE:
        opString = "<=";
        break;
      case LOR:
        opString = "||";
        break;
      case LT:
        opString = "<";
        break;
      case LXOR:
        opString = "^^";
        break;
      case MOD:
        opString = "%";
        break;
      case MOD_ASSIGN:
        opString = "%=";
        break;
      case MUL:
        opString = "*";
        break;
      case MUL_ASSIGN:
        opString = "*=";
        break;
      case NE:
        opString = "!=";
        break;
      case SHL:
        opString = "<<";
        break;
      case SHL_ASSIGN:
        opString = "<<=";
        break;
      case SHR:
        opString = ">>";
        break;
      case SHR_ASSIGN:
        opString = ">>=";
        break;
      case SUB:
        opString = "-";
        break;
      case SUB_ASSIGN:
        opString = "-=";
        break;
      default:
        assert false;
        break;
    }

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
  public List<List<? extends Type>> getArgumentTypes() {
    return Arrays.asList(lhsTypes, rhsTypes);
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

  public BinOp getOp() {
    return op;
  }

  @Override
  protected String getTemplateName() {
    return "BINARY(" + op + ")";
  }

}
