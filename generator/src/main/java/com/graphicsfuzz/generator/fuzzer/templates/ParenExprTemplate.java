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
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParenExprTemplate extends AbstractExprTemplate {

  private Type type;
  private boolean isLValue;

  public ParenExprTemplate(Type type, boolean isLValue) {
    this.type = type;
    this.isLValue = isLValue;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new ParenExpr(args[0]);
  }

  @Override
  public Type getResultType() {
    return type;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return Arrays.asList(new ArrayList<Type>(Arrays.asList(type)));
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0;
    return isLValue();
  }

  @Override
  public boolean isLValue() {
    return isLValue;
  }

  @Override
  public int getNumArguments() {
    return 1;
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  protected String getTemplateName() {
    return "PAREN";
  }

}
