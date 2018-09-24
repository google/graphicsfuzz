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

package com.graphicsfuzz.common.ast.expr;

public enum BinOp implements Op {
  COMMA,
  MOD,
  MUL,
  DIV,
  ADD,
  SUB,
  BAND,
  BOR,
  BXOR,
  LAND,
  LOR,
  LXOR,
  SHL,
  SHR,
  LT,
  GT,
  LE,
  GE,
  EQ,
  NE,
  ASSIGN,
  MUL_ASSIGN,
  DIV_ASSIGN,
  MOD_ASSIGN,
  ADD_ASSIGN,
  SUB_ASSIGN,
  BAND_ASSIGN,
  BOR_ASSIGN,
  BXOR_ASSIGN,
  SHL_ASSIGN,
  SHR_ASSIGN;

  @Override
  public String getText() {
    switch (this) {
      case COMMA:
        return ",";
      case MOD:
        return "%";
      case MUL:
        return "*";
      case DIV:
        return "/";
      case ADD:
        return "+";
      case SUB:
        return "-";
      case BAND:
        return "&";
      case BOR:
        return "|";
      case BXOR:
        return "^";
      case LAND:
        return "&&";
      case LOR:
        return "||";
      case LXOR:
        return "^^";
      case SHL:
        return "<<";
      case SHR:
        return ">>";
      case LT:
        return "<";
      case GT:
        return ">";
      case LE:
        return "<=";
      case GE:
        return ">=";
      case EQ:
        return "==";
      case NE:
        return "!=";
      case ASSIGN:
        return "=";
      case MUL_ASSIGN:
        return "*=";
      case DIV_ASSIGN:
        return "/=";
      case MOD_ASSIGN:
        return "%=";
      case ADD_ASSIGN:
        return "+=";
      case SUB_ASSIGN:
        return "-=";
      case BAND_ASSIGN:
        return "&=";
      case BOR_ASSIGN:
        return "|=";
      case BXOR_ASSIGN:
        return "^=";
      case SHL_ASSIGN:
        return "<<=";
      default:
        assert this == SHR_ASSIGN : "Unknown binary operator " + this;
        return ">>=";
    }
  }

  @Override
  public boolean isSideEffecting() {
    switch (this) {
      case ASSIGN:
      case MUL_ASSIGN:
      case DIV_ASSIGN:
      case MOD_ASSIGN:
      case ADD_ASSIGN:
      case SUB_ASSIGN:
      case BAND_ASSIGN:
      case BOR_ASSIGN:
      case BXOR_ASSIGN:
      case SHL_ASSIGN:
      case SHR_ASSIGN:
        return true;
      default:
        return false;
    }
  }

}
