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

import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ConstantExprTemplate extends AbstractExprTemplate {

  private final BasicType type;

  public ConstantExprTemplate(BasicType type) {
    this.type = type;
  }

  @Override
  public Expr generateExpr(IRandom generator, Expr... args) {
    assert args.length == getNumArguments();

    final Supplier<ConstantExpr> floatLiteralSupplier = () -> randomFloatLiteral(generator);
    final Supplier<ConstantExpr> intLiteralSupplier = () -> randomIntLiteral(generator);
    final Supplier<ConstantExpr> uintLiteralSupplier = () -> randomUintLiteral(generator);
    final Supplier<ConstantExpr> boolLiteralSupplier = () -> randomBoolLiteral(generator);

    if (type == BasicType.FLOAT) {
      return floatLiteralSupplier.get();
    }
    if (type == BasicType.VEC2) {
      return makeTypeConstructor(BasicType.VEC2, floatLiteralSupplier, 2);
    }
    if (type == BasicType.VEC3) {
      return makeTypeConstructor(BasicType.VEC3, floatLiteralSupplier, 3);
    }
    if (type == BasicType.VEC4) {
      return makeTypeConstructor(BasicType.VEC4, floatLiteralSupplier, 4);
    }
    if (type == BasicType.INT) {
      return intLiteralSupplier.get();
    }
    if (type == BasicType.IVEC2) {
      return makeTypeConstructor(BasicType.IVEC2, intLiteralSupplier, 2);
    }
    if (type == BasicType.IVEC3) {
      return makeTypeConstructor(BasicType.IVEC3, intLiteralSupplier, 3);
    }
    if (type == BasicType.IVEC4) {
      return makeTypeConstructor(BasicType.IVEC4, intLiteralSupplier, 4);
    }
    if (type == BasicType.UINT) {
      return uintLiteralSupplier.get();
    }
    if (type == BasicType.UVEC2) {
      return makeTypeConstructor(BasicType.UVEC2, uintLiteralSupplier, 2);
    }
    if (type == BasicType.UVEC3) {
      return makeTypeConstructor(BasicType.UVEC3, uintLiteralSupplier, 3);
    }
    if (type == BasicType.UVEC4) {
      return makeTypeConstructor(BasicType.UVEC4, uintLiteralSupplier, 4);
    }
    if (type == BasicType.BOOL) {
      return boolLiteralSupplier.get();
    }
    if (type == BasicType.BVEC2) {
      return makeTypeConstructor(BasicType.BVEC2, boolLiteralSupplier, 2);
    }
    if (type == BasicType.BVEC3) {
      return makeTypeConstructor(BasicType.BVEC3, boolLiteralSupplier, 3);
    }
    if (type == BasicType.BVEC4) {
      return makeTypeConstructor(BasicType.BVEC4, boolLiteralSupplier, 4);
    }
    if (type == BasicType.MAT2X2) {
      return makeTypeConstructor(BasicType.MAT2X2, floatLiteralSupplier, 4);
    }
    if (type == BasicType.MAT2X3) {
      return makeTypeConstructor(BasicType.MAT2X3, floatLiteralSupplier, 6);
    }
    if (type == BasicType.MAT2X4) {
      return makeTypeConstructor(BasicType.MAT2X4, floatLiteralSupplier, 8);
    }
    if (type == BasicType.MAT3X2) {
      return makeTypeConstructor(BasicType.MAT3X2, floatLiteralSupplier, 6);
    }
    if (type == BasicType.MAT3X3) {
      return makeTypeConstructor(BasicType.MAT3X3, floatLiteralSupplier, 9);
    }
    if (type == BasicType.MAT3X4) {
      return makeTypeConstructor(BasicType.MAT3X4, floatLiteralSupplier, 12);
    }
    if (type == BasicType.MAT4X2) {
      return makeTypeConstructor(BasicType.MAT4X2, floatLiteralSupplier, 8);
    }
    if (type == BasicType.MAT4X3) {
      return makeTypeConstructor(BasicType.MAT4X3, floatLiteralSupplier, 12);
    }
    if (type == BasicType.MAT4X4) {
      return makeTypeConstructor(BasicType.MAT4X4, floatLiteralSupplier, 16);
    }
    throw new RuntimeException("Unknown type " + type);
  }

  private Expr makeTypeConstructor(BasicType type, Supplier<ConstantExpr> supplier, int width) {
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < width; i++) {
      args.add(supplier.get());
    }
    return new TypeConstructorExpr(type.toString(), args);
  }

  private BoolConstantExpr randomBoolLiteral(IRandom generator) {
    return generator.nextBoolean() ? BoolConstantExpr.TRUE : BoolConstantExpr.FALSE;
  }

  private IntConstantExpr randomIntLiteral(IRandom generator) {
    final int maxValue = 200000;
    return new IntConstantExpr(String.valueOf(generator.nextInt(maxValue) - (maxValue / 2)));
  }

  private UIntConstantExpr randomUintLiteral(IRandom generator) {
    final int maxValue = 200000;
    return new UIntConstantExpr(String.valueOf(generator.nextInt(maxValue)) + "u");
  }

  private FloatConstantExpr randomFloatLiteral(IRandom generator) {
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
    return new FloatConstantExpr(sb.toString());
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
    throw new IndexOutOfBoundsException("Constant expression has no arguments");
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
    return "CONSTANT";
  }

}
