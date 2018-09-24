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
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SwizzleExprTemplate extends AbstractExprTemplate {

  private final BasicType argType;
  private final BasicType resultType;
  private final boolean isLValue;

  public SwizzleExprTemplate(BasicType argType, BasicType resultType, boolean isLValue) {
    assert BasicType.allVectorTypes().contains(argType);
    assert BasicType.allVectorTypes().contains(resultType) || BasicType.allScalarTypes()
          .contains(resultType);
    if (isLValue) {
      assert resultType.getNumElements() <= argType.getNumElements();
    }
    this.argType = argType;
    this.resultType = resultType;
    this.isLValue = isLValue;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {

    assert args.length == getNumArguments();

    String[] mapping = chooseVectorComponentNamingScheme(generator);

    String swizzleIndices = "";
    int nextComponent = 0;
    while (nextComponent < resultType.getNumElements()) {
      String candidate = mapping[generator.nextInt(argType.getNumElements())];
      if (isLValue() && swizzleIndices.contains(candidate)) {
        continue;
      }
      swizzleIndices += candidate;
      nextComponent++;
    }

    return new MemberLookupExpr(args[0], swizzleIndices);

  }

  private static String[] chooseVectorComponentNamingScheme(IRandom generator) {
    String[] mapping;
    switch (generator.nextInt(3)) {
      case 0:
        mapping = new String[]{"r", "g", "b", "a"};
        break;
      case 1:
        mapping = new String[]{"s", "t", "p", "q"};
        break;
      default:
        mapping = new String[]{"x", "y", "z", "w"};
    }
    return mapping;
  }

  @Override
  public Type getResultType() {
    return resultType;
  }

  @Override
  public List<List<? extends Type>> getArgumentTypes() {
    return new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(argType))));
  }

  @Override
  public boolean requiresLValueForArgument(int index) {
    assert index == 0;
    return isLValue;
  }

  @Override
  public boolean isLValue() {
    return isLValue;
  }

  @Override
  public boolean isConst() {
    return true;
  }

  @Override
  public int getNumArguments() {
    return 1;
  }

  @Override
  protected String getTemplateName() {
    return "SWIZZLE";
  }

}
