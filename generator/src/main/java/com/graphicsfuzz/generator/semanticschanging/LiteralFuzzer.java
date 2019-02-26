/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LiteralFuzzer {

  private static final int INT_MIN = - (1 << 17);
  private static final int INT_MAX = 1 << 17;

  private final IRandom generator;

  public LiteralFuzzer(IRandom generator) {
    this.generator = generator;
  }

  public Optional<Expr> fuzz(Type type) {
    if (type == BasicType.BOOL) {
      return Optional.of(generator.nextBoolean()
          ? new BoolConstantExpr(true)
          : new BoolConstantExpr(false));
    }
    if (type == BasicType.INT) {
      return Optional.of(new IntConstantExpr(
            String.valueOf(generator.nextInt(INT_MAX - INT_MIN) + INT_MIN)));
    }
    if (type == BasicType.FLOAT) {
      return Optional.of(new FloatConstantExpr(
            randomFloatString()));
    }
    if (type == BasicType.VEC2 || type == BasicType.VEC3 || type == BasicType.VEC4
          || BasicType.allMatrixTypes().contains(type)) {
      final List<Expr> args = new ArrayList<>();
      for (int i = 0; i < ((BasicType) type).getNumElements(); i++) {
        args.add(fuzz(((BasicType) type).getElementType()).get());
      }
      return Optional.of(new TypeConstructorExpr(type.toString(), args));
    }
    return Optional.empty();
  }

  private String randomFloatString() {
    final int maxDigitsEitherSide = 5;
    StringBuilder sb = new StringBuilder();
    sb.append(generator.nextBoolean() ? "-" : "");
    int digitsBefore = Math.max(1, generator.nextInt(maxDigitsEitherSide));
    for (int i = 0; i < digitsBefore; i++) {
      int candidate;
      while (true) {
        candidate = generator.nextInt(10);
        if (candidate == 0 && i == 0 && digitsBefore > 1) {
          continue;
        }
        break;
      }
      sb.append(String.valueOf(candidate));
    }
    sb.append(".");
    for (int i = 0; i < digitsBefore; i++) {
      sb.append(String.valueOf(generator.nextInt(10)));
    }
    return sb.toString();
  }

}
