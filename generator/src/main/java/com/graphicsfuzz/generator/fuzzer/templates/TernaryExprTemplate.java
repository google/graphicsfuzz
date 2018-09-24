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
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TernaryExprTemplate extends AbstractExprTemplate {

  private Type resultType;

  public TernaryExprTemplate(Type resultType) {
    this.resultType = resultType;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new ParenExpr(new TernaryExpr(args[0], args[1], args[2]));
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return Arrays.asList(
        new ArrayList<Type>(Arrays.asList(BasicType.BOOL)),
        new ArrayList<Type>(Arrays.asList(resultType)),
        new ArrayList<Type>(Arrays.asList(resultType))
    );
  }

  @Override
  public int getNumArguments() {
    return 3;
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0 || index == 1 || index == 2;
    return false;
  }

  @Override
  public boolean isLValue() {
    return false;
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  protected String getTemplateName() {
    return "TERNARY";
  }

}
