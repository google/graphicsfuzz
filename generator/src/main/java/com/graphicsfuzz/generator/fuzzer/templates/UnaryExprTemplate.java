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

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UnaryExprTemplate extends AbstractExprTemplate {

  private Type argType;
  private Type resultType;
  private UnOp op;

  public UnaryExprTemplate(Type argType, Type resultType, UnOp op) {
    this.argType = argType;
    this.resultType = resultType;
    this.op = op;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new ParenExpr(new UnaryExpr(args[0], op));
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return Arrays.asList(
        new ArrayList<Type>(Arrays.asList(argType)));
  }

  @Override
  public int getNumArguments() {
    return 1;
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0;
    switch (op) {
      case POST_DEC:
      case POST_INC:
      case PRE_DEC:
      case PRE_INC:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean isLValue() {
    return false;
  }

  @Override
  public boolean isConst() {
    switch (op) {
      case POST_DEC:
      case POST_INC:
      case PRE_DEC:
      case PRE_INC:
        return false;
      default:
        return true;
    }
  }

  @Override
  protected String getTemplateName() {
    return "UNARY(" + op + ")";
  }

}
