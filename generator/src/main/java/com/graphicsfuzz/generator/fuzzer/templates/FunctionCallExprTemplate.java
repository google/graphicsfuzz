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

import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionCallExprTemplate extends AbstractExprTemplate {

  private final String name;
  private final Type resultType;
  private final List<Type> argTypes;

  public FunctionCallExprTemplate(FunctionPrototype prototype) {
    this.name = prototype.getName();
    this.resultType = prototype.getReturnType();
    this.argTypes = new ArrayList<>();
    for (ParameterDecl param : prototype.getParameters()) {
      assert param.getArrayInfo() == null;
      this.argTypes.add(param.getType());
    }
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    return new FunctionCallExpr(name, Arrays.asList(args));
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<Type>> getArgumentTypes() {
    return argTypes.stream().map(Collections::singletonList)
        .collect(Collectors.toList());
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index >= 0 && index < getNumArguments();
    return argTypes.get(index).hasQualifier(TypeQualifier.OUT_PARAM)
        || argTypes.get(index).hasQualifier(TypeQualifier.INOUT_PARAM);
  }

  @Override
  public boolean isLValue() {
    return false;
  }

  @Override
  public boolean isConst() {
    return false;
  }

  @Override
  public int getNumArguments() {
    return argTypes.size();
  }

  @Override
  protected String getTemplateName() {
    return "FUNCTION(" + name + ")";
  }

}
