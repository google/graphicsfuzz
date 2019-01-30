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
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.List;

public class VariableIdentifierExprTemplate extends AbstractExprTemplate {

  private String name;
  private Type type;
  private List<TypeQualifier> qualifiers;

  public VariableIdentifierExprTemplate(String name, Type possiblyQualifiedType) {
    this.name = name;
    if (possiblyQualifiedType instanceof QualifiedType) {
      this.type = ((QualifiedType) possiblyQualifiedType).getTargetType();
      this.qualifiers = ((QualifiedType) possiblyQualifiedType).getQualifiers();
    } else {
      this.type = possiblyQualifiedType;
      this.qualifiers = new ArrayList<>();
    }
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();
    return new VariableIdentifierExpr(name);
  }

  @Override
  public Type getResultType() {
    return type;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return new ArrayList<>();
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
    if (qualifiers.contains(TypeQualifier.ATTRIBUTE)) {
      return false;
    }
    if (qualifiers.contains(TypeQualifier.CONST)) {
      return false;
    }
    if (qualifiers.contains(TypeQualifier.SHADER_INPUT)) {
      return false;
    }
    if (qualifiers.contains(TypeQualifier.UNIFORM)) {
      return false;
    }
    return true;
  }

  @Override
  public boolean isConst() {
    return qualifiers.contains(TypeQualifier.CONST);
  }

  @Override
  protected String getTemplateName() {
    return "VARIABLE" + name;
  }

}
