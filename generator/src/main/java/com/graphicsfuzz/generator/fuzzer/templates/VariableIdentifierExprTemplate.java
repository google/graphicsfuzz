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
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.IRandom;
import java.util.Collections;
import java.util.List;

public class VariableIdentifierExprTemplate extends AbstractExprTemplate {

  // The name of the variable.
  private final String name;

  // The type of the variable.
  private final Type type;

  // True if and only if the variable is a function formal parameter.  This is important because we
  // should not regard such parameters as safe to use in a 'const' context even if they are const-
  // qualified, because they are not compile-time constants.
  private final boolean isFunctionParameter;

  public VariableIdentifierExprTemplate(String name, Type type,
                                        boolean isFunctionParameter) {
    this.name = name;
    this.type = type;
    this.isFunctionParameter = isFunctionParameter;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new VariableIdentifierExpr(name);
  }

  @Override
  public Type getResultType() {
    // We return an unqualified version of the type, since during fuzzing we are looking for matches
    // on underlying types; other methods such as 'isLValue' are sensitive to the qualifiers of the
    // type.
    return type.getWithoutQualifiers();
  }

  @Override
  public List<List<Type>> getArgumentTypes() {
    return Collections.emptyList();
  }

  @Override
  public int getNumArguments() {
    return 0;
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    throw new IndexOutOfBoundsException("Variable identifier expression takes no arguments");
  }

  @Override
  public boolean isLValue() {
    if (type.hasQualifier(TypeQualifier.ATTRIBUTE)) {
      return false;
    }
    if (type.hasQualifier(TypeQualifier.CONST)) {
      return false;
    }
    if (type.hasQualifier(TypeQualifier.SHADER_INPUT)) {
      return false;
    }
    if (type.hasQualifier(TypeQualifier.UNIFORM)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isConst() {
    return !isFunctionParameter && type.hasQualifier(TypeQualifier.CONST);
  }

  @Override
  protected String getTemplateName() {
    return "VARIABLE" + name;
  }

}
