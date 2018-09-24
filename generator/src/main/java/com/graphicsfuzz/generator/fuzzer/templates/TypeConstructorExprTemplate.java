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
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TypeConstructorExprTemplate extends AbstractExprTemplate {

  private BasicType resultType;
  private List<BasicType> argTypes;

  public TypeConstructorExprTemplate(BasicType resultType, BasicType... argTypes) {
    this.resultType = resultType;
    this.argTypes = Arrays.asList(argTypes);
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new TypeConstructorExpr(resultType.toString(), Arrays.asList(args));
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return argTypes.stream().map(x -> new ArrayList<Type>(
        Arrays.asList(x))).collect(Collectors.toList());
  }

  @Override
  public int getNumArguments() {
    return argTypes.size();
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index >= 0 && index < getNumArguments();
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
    return "TYPE_CONSTRUCTOR";
  }

}
