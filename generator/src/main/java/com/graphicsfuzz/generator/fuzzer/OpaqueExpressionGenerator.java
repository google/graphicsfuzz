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

package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.SideEffectChecker;
import com.graphicsfuzz.generator.transformation.ExpressionIdentity;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class OpaqueExpressionGenerator {

  private final IRandom generator;
  private final GenerationParams generationParams;

  // The available identities that can be applied to expressions.
  private final List<ExpressionIdentity> expressionIdentities;

  private final ShadingLanguageVersion shadingLanguageVersion;

  public OpaqueExpressionGenerator(IRandom generator, GenerationParams generationParams,
                                   ShadingLanguageVersion shadingLanguageVersion) {
    this.generator = generator;
    this.generationParams = generationParams;
    // TODO: there are many more identities that we can easily play with here, e.g. bitwise and 1
    // for integer types
    this.expressionIdentities = new ArrayList<>();
    this.shadingLanguageVersion = shadingLanguageVersion;

    expressionIdentities.add(new IdentityAddSubZero());
    expressionIdentities.add(new IdentityMulDivOne());
    expressionIdentities.add(new IdentityAndTrue());
    expressionIdentities.add(new IdentityOrFalse());
    expressionIdentities.add(new IdentityNotNot());
    expressionIdentities.add(new IdentityTernary());

    expressionIdentities.add(new IdentityMin());
    expressionIdentities.add(new IdentityMax());
    expressionIdentities.add(new IdentityClamp());

    if (shadingLanguageVersion.supportedMixNonfloatBool()) {
      expressionIdentities.add(new IdentityMixBvec());
    }

  }

  private List<OpaqueZeroOneFactory> waysToMakeZeroOrOne() {
    return Arrays.asList(
        this::opaqueZeroOrOneFromIdentityFunction,
        this::opaqueZeroOrOneFromInjectionSwitch,
        this::opaqueZeroOrOneSquareRoot,
        this::opaqueZeroOrOneAbsolute
    );
  }

  private List<OpaqueZeroOneFactory> waysToMakeZero() {
    List<OpaqueZeroOneFactory> opaqueOneFactories = new ArrayList<>();
    opaqueOneFactories.addAll(waysToMakeZeroOrOne());
    opaqueOneFactories.add(this::opaqueZeroSin);
    opaqueOneFactories.add(this::opaqueZeroLogarithm);
    opaqueOneFactories.add(this::opaqueZeroTan);
    opaqueOneFactories.add(this::opaqueZeroVectorLength);
    return opaqueOneFactories;
  }

  private List<OpaqueZeroOneFactory> waysToMakeOne() {
    List<OpaqueZeroOneFactory> opaqueZeroFactories = new ArrayList<>();
    opaqueZeroFactories.addAll(waysToMakeZeroOrOne());
    opaqueZeroFactories.add(this::opaqueOneExponential);
    opaqueZeroFactories.add(this::opaqueOneCosine);
    opaqueZeroFactories.add(this::opaqueOneNormalizedVectorLength);
    return opaqueZeroFactories;
  }

  private Optional<Expr> opaqueZeroOrOneFromIdentityFunction(BasicType type, boolean constContext,
                                                             final int depth, Fuzzer fuzzer,
                                                             boolean isZero) {
    // Make an opaque value recursively and apply an identity function to it
    return Optional.of(applyIdentityFunction(makeOpaqueZeroOrOne(isZero, type, constContext,
        depth, fuzzer),
        type,
        constContext,
        depth,
        fuzzer));
  }

  private Optional<Expr> opaqueZeroOrOneFromInjectionSwitch(BasicType type, boolean constContext,
                                                            final int depth, Fuzzer fuzzer,
                                                            boolean isZero) {
    // injectionSwitch.x or injectionSwitch.y
    if (constContext || !generationParams.getInjectionSwitchIsAvailable()) {
      return Optional.empty();
    }
    return Optional.of(makeOpaqueZeroOrOneFromInjectionSwitch(isZero, type));
  }

  private Optional<Expr> opaqueZeroOrOneSquareRoot(BasicType type, boolean constContext,
                                                   final int depth, Fuzzer fuzzer, boolean isZero) {
    // sqrt (opaque)
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("sqrt", makeOpaqueZeroOrOne(isZero, type, constContext,
        depth, fuzzer)));
  }

  private Optional<Expr> opaqueZeroOrOneAbsolute(BasicType type, boolean constContext,
                                                   final int depth, Fuzzer fuzzer, boolean isZero) {
    // abs (opaque)
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("abs", makeOpaqueZeroOrOne(isZero, type, constContext,
        depth, fuzzer)));
  }

  private Optional<Expr> opaqueZeroSin(BasicType type, boolean constContext, final int depth,
                                       Fuzzer fuzzer, boolean isZero) {
    // represent 0 as sin(opaqueZero) function, e.g. sin(0.0)
    assert isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("sin", makeOpaqueZero(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueZeroLogarithm(BasicType type, boolean constContext, final int depth,
                                             Fuzzer fuzzer, boolean isZero) {
    // represent 0 as the natural logarithm of opaqueOne, e.g. log(1.0)
    assert isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("log", makeOpaqueOne(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueZeroTan(BasicType type, boolean constContext, final int depth,
                                             Fuzzer fuzzer, boolean isZero) {
    // represent 0 as tan(opaqueZero) function, e.g. tan(0.0)
    assert isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("tan", makeOpaqueZero(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueZeroVectorLength(BasicType type, boolean constContext,
                                                final int depth,
                                                Fuzzer fuzzer, boolean isZero) {
    // represent 0 as the length of zero vector, e.g. length(opaqueZero).
    assert isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("length", makeOpaqueZero(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueOneExponential(BasicType type, boolean constContext, final int depth,
                                              Fuzzer fuzzer, boolean isZero) {
    // represent 1 as the exponential function of opaqueZero, e.g. exp(0.0)
    assert !isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("exp", makeOpaqueZero(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueOneCosine(BasicType type, boolean constContext, final int depth,
                                         Fuzzer fuzzer, boolean isZero) {
    // represent 1 as cos(opaqueZero) function, e.g. cos(0.0)
    assert !isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    return Optional.of(new FunctionCallExpr("cos", makeOpaqueZero(type, constContext, depth,
        fuzzer)));
  }

  private Optional<Expr> opaqueOneNormalizedVectorLength(BasicType type, boolean constContext,
                                                         final int depth,
                                                         Fuzzer fuzzer, boolean isZero) {
    // represent 1 as the length of normalized vector
    assert !isZero;
    if (!BasicType.allGenTypes().contains(type)) {
      return Optional.empty();
    }
    Expr normalizedExpr = new FunctionCallExpr("normalize", makeOpaqueZeroOrOne(isZero,
        type, constContext, depth, fuzzer));
    return Optional.of(new FunctionCallExpr("length", normalizedExpr));
  }

  private List<BasicType> numericTypesOrGenTypesIfEssl100() {
    if (shadingLanguageVersion == ShadingLanguageVersion.ESSL_100
        || shadingLanguageVersion == ShadingLanguageVersion.WEBGL_SL) {
      return BasicType.allGenTypes();
    }
    return BasicType.allNonMatrixNumericTypes();
  }

  public Expr applyIdentityFunction(Expr expr, BasicType type, boolean constContext,
                                    final int depth,
                                    Fuzzer fuzzer) {
    if (isTooDeep(depth)) {
      return expr;
    }
    final List<ExpressionIdentity> availableTransformations =
        expressionIdentities.stream()
            .filter(item -> item.preconditionHolds(expr, type))
            .collect(Collectors.toList());
    return availableTransformations.isEmpty() ? expr :
        availableTransformations.get(generator.nextInt(availableTransformations.size()))
            .apply(expr, type, constContext, depth + 1, fuzzer);
  }

  private boolean isTooDeep(int depth) {
    return Fuzzer.isTooDeep(depth, generationParams, generator);
  }

  private Expr applyBinaryIdentityFunction(Expr expr, Expr opaqueExpr, BinOp operator,
                                           boolean considerReverseDirection,
                                           BasicType type,
                                           boolean constContext,
                                           final int depth, Fuzzer fuzzer) {
    Expr exprWithIdentityApplied = new ParenExpr(applyIdentityFunction(expr, type, constContext,
        depth, fuzzer));
    if (!considerReverseDirection || generator.nextBoolean()) {
      return identityConstructor(expr,
          new BinaryExpr(exprWithIdentityApplied, opaqueExpr, operator));
    }
    return identityConstructor(expr, new BinaryExpr(opaqueExpr, exprWithIdentityApplied, operator));
  }

  public Expr makeOpaqueZero(BasicType type, boolean constContext, final int depth,
                             Fuzzer fuzzer) {
    return makeOpaqueZeroOrOne(true, type, constContext, depth, fuzzer);
  }

  public Expr makeOpaqueOne(BasicType type, boolean constContext, final int depth,
                            Fuzzer fuzzer) {
    return makeOpaqueZeroOrOne(false, type, constContext, depth, fuzzer);
  }

  private Expr makeOpaqueZeroOrOne(boolean isZero, BasicType type, boolean constContext,
                                   final int depth, Fuzzer fuzzer) {

    if (isTooDeep(depth)) {
      return makeLiteralZeroOrOne(isZero, type);
    }
    final int newDepth = depth + 1;

    // If isZero holds, we are making an opaque zero, otherwise an opaque one
    final List<OpaqueZeroOneFactory> opaqueFactories = isZero ? waysToMakeZero() : waysToMakeOne();
    while (!opaqueFactories.isEmpty()) {
      final int index = generator.nextInt(opaqueFactories.size());
      final OpaqueZeroOneFactory factory = opaqueFactories.get(index);
      final Optional<Expr> maybeResult = factory.tryMakeOpaque(type, constContext, newDepth, fuzzer,
          isZero);

      if (maybeResult.isPresent()) {
        return maybeResult.get();
      }
      opaqueFactories.remove(index);
    }
    throw new RuntimeException("Could not find any compatible opaque expressions of type "
        + type.toString());
  }

  private Expr makeOpaqueZeroOrOneFromInjectionSwitch(boolean isZero, BasicType type) {
    assert generationParams.getInjectionSwitchIsAvailable();
    if (type == BasicType.FLOAT) {
      if (isZero) {
        return zeroConstructor(injectionSwitch("x"), type);
      }
      return oneConstructor(injectionSwitch("y"), type);
    }
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < type.getNumElements(); i++) {
      // We use float for each argument, on the assumption that all the scalar and vector
      // constructors can take float arguments
      args.add(makeOpaqueZeroOrOneFromInjectionSwitch(isZero, BasicType.FLOAT));
    }
    return new TypeConstructorExpr(type.toString(), args);
  }

  private Expr makeRegularZero(BasicType type) {
    return makeLiteralZeroOrOne(true, type);
  }

  private Expr makeRegularOne(BasicType type) {
    return makeLiteralZeroOrOne(false, type);
  }

  private Expr makeOpaqueIdentityMatrix(BasicType type, boolean constContext,
                                        final int depth, Fuzzer fuzzer) {
    assert BasicType.allSquareMatrixTypes().contains(type);
    if (isTooDeep(depth)) {
      return new TypeConstructorExpr(type.toString(), makeRegularOne(BasicType.FLOAT));
    }
    final int newDepth = depth + 1;
    while (true) {
      final int numTypesOfIdentityMatrix = 2;
      switch (generator.nextInt(numTypesOfIdentityMatrix)) {
        case 0:
          // Make an opaque identity matrix recursively and apply an identity function to it
          return applyIdentityFunction(makeOpaqueIdentityMatrix(type, constContext,
              newDepth, fuzzer),
              type,
              constContext,
              newDepth,
              fuzzer);
        case 1:
          // Use injectionSwitch.y
          if (constContext || !generationParams.getInjectionSwitchIsAvailable()) {
            continue;
          }
          return new TypeConstructorExpr(type.toString(), oneConstructor(
              injectionSwitch("y"), BasicType.FLOAT));
        default:
          throw new RuntimeException();
      }
    }
  }


  private Expr makeOpaqueBooleanScalar(boolean value, boolean constContext, final int depth,
                                       Fuzzer fuzzer) {

    if (isTooDeep(depth)) {
      return value ? new BoolConstantExpr(true) : new BoolConstantExpr(false);
    }
    Expr result = null;
    final int newDepth = depth + 1;
    if (constContext) {
      return recursivelyMakeOpaqueBooleanScalar(value, constContext, fuzzer, newDepth);
    }
    final int numTypesOfBool = generationParams.getShaderKind() == ShaderKind.FRAGMENT ? 4 : 2;
    final Function<Expr, Expr> constructorFunction =
        value ? this::trueConstructor : this::falseConstructor;
    while (true) {
      switch (generator.nextInt(numTypesOfBool)) {
        case 0:
          // Make an opaque boolean value recursively and apply an identity function to it
          return recursivelyMakeOpaqueBooleanScalar(value, constContext, fuzzer, newDepth);
        case 1: {
          if (!generationParams.getInjectionSwitchIsAvailable()) {
            continue;
          }
          return makeOpaqueBooleanScalarFromExpr(value,
              new BinaryExpr(injectionSwitch("x"), injectionSwitch("y"),
                  value ? BinOp.LT : BinOp.GT));
        }
        case 2: {
          // gl_FragCoord.x [op] 0
          assert generationParams.getShaderKind() == ShaderKind.FRAGMENT;
          return makeOpaqueBooleanScalarFromExpr(value,
              compareWithGlFragCoord(value, constContext, fuzzer, newDepth, "x"));
        }
        case 3: {
          // gl_FragCoord.y [op] 0
          assert generationParams.getShaderKind() == ShaderKind.FRAGMENT;
          return makeOpaqueBooleanScalarFromExpr(value,
              compareWithGlFragCoord(value, constContext, fuzzer, newDepth, "y"));
        }
        default:
          throw new RuntimeException();
      }
    }
  }

  private Expr makeOpaqueBooleanScalarFromExpr(boolean value, Expr expr) {
    final Function<Expr, Expr> constructorFunction =
        value ? this::trueConstructor : this::falseConstructor;
    return constructorFunction
        .apply(new ParenExpr(expr));
  }

  private BinaryExpr compareWithGlFragCoord(boolean value, boolean constContext, Fuzzer fuzzer,
                                            int newDepth, String coord) {
    return new BinaryExpr(
        new MemberLookupExpr(new VariableIdentifierExpr(OpenGlConstants.GL_FRAG_COORD), coord),
        makeOpaqueZero(BasicType.FLOAT, constContext, newDepth, fuzzer),
        value ? BinOp.GE : BinOp.LT);
  }

  private Expr recursivelyMakeOpaqueBooleanScalar(boolean value, boolean constContext,
                                                  Fuzzer fuzzer,
                                                  int newDepth) {
    return applyIdentityFunction(makeOpaqueBooleanScalar(value, constContext,
        newDepth, fuzzer),
        BasicType.BOOL,
        constContext,
        newDepth,
        fuzzer);
  }


  public Expr makeOpaqueBoolean(boolean value, BasicType type, boolean constContext,
                                final int depth, Fuzzer fuzzer) {
    assert type.isBoolean();
    if (type == BasicType.BOOL) {
      return makeOpaqueBooleanScalar(value, constContext, depth, fuzzer);
    }
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < type.getNumElements(); i++) {
      args.add(makeOpaqueBoolean(value, BasicType.BOOL, constContext, depth, fuzzer));
    }
    return new TypeConstructorExpr(type.toString(), args);
  }

  public Expr makeDeadCondition(Fuzzer fuzzer) {
    return new FunctionCallExpr(Constants.GLF_DEAD,
        makeOpaqueBoolean(false, BasicType.BOOL, false, 0, fuzzer));
  }

  private Expr injectionSwitch(String dimension) {
    assert generationParams.getInjectionSwitchIsAvailable();
    return new MemberLookupExpr(new VariableIdentifierExpr(Constants.INJECTION_SWITCH), dimension);
  }

  private Expr macroConstructor(String name, Expr... args) {
    return new FunctionCallExpr(name, Arrays.asList(args));
  }

  public Expr fuzzedConstructor(Expr expr) {
    return macroConstructor(Constants.GLF_FUZZED, expr);
  }

  private Expr identityConstructor(Expr original, Expr withIdentityApplied) {
    return macroConstructor(Constants.GLF_IDENTITY, original.clone(), withIdentityApplied);
  }

  private Expr zeroConstructor(Expr expr, BasicType type) {
    return macroConstructor(Constants.GLF_ZERO, makeRegularZero(type), expr);
  }

  private Expr oneConstructor(Expr expr, BasicType type) {
    return macroConstructor(Constants.GLF_ONE, makeRegularOne(type), expr);
  }

  private Expr falseConstructor(Expr expr) {
    return macroConstructor(Constants.GLF_FALSE, new BoolConstantExpr(false), expr);
  }

  private Expr trueConstructor(Expr expr) {
    return macroConstructor(Constants.GLF_TRUE, new BoolConstantExpr(true), expr);
  }

  private Expr makeLiteralZeroOrOne(boolean isZero, BasicType type) {
    assert type.getElementType() != BasicType.BOOL;
    assert isZero || !BasicType.allNonSquareMatrixTypes().contains(type);

    final String integerPart = isZero ? "0" : "1";

    if (type == BasicType.FLOAT) {
      return new FloatConstantExpr(integerPart + ".0");
    }
    if (type == BasicType.INT) {
      return new IntConstantExpr(integerPart);
    }
    if (type == BasicType.UINT) {
      return new UIntConstantExpr(integerPart + "u");
    }
    List<Expr> args = new ArrayList<>();
    for (int i = 0; i < type.getNumElements(); i++) {
      assert type.getElementType() != type;
      args.add(makeLiteralZeroOrOne(isZero, type.getElementType()));
    }
    return new TypeConstructorExpr(type.toString(), args);
  }

  private Expr ternary(Expr condition, Expr thenExpr, Expr elseExpr) {
    return new ParenExpr(new TernaryExpr(condition, thenExpr, elseExpr));
  }

  private abstract class AbstractIdentityTransformation implements ExpressionIdentity {

    private final Collection<BasicType> acceptableTypes;
    private final boolean exprMustBeSideEffectFree;

    AbstractIdentityTransformation(Collection<BasicType> acceptableTypes,
                                   boolean exprMustBeSideEffectFree) {
      this.acceptableTypes = acceptableTypes;
      this.exprMustBeSideEffectFree = exprMustBeSideEffectFree;
    }

    @Override
    public final boolean preconditionHolds(Expr expr, BasicType basicType) {
      if (!acceptableTypes.contains(basicType)) {
        return false;
      }
      if (!exprMustBeSideEffectFree) {
        return true;
      }
      return SideEffectChecker.isSideEffectFree(expr, shadingLanguageVersion);

    }

  }

  private class IdentityAddSubZero extends AbstractIdentityTransformation {

    private IdentityAddSubZero() {
      super(BasicType.allNumericTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // expr + 0
      // or
      // 0 + x
      // or
      // x - 0
      assert type != BasicType.BOOL;
      final BinOp operator = generator.nextBoolean() ? BinOp.ADD : BinOp.SUB;
      return applyBinaryIdentityFunction(expr, makeOpaqueZero(type, constContext,
          depth, fuzzer), operator,
          operator == BinOp.ADD, type, constContext,
          depth, fuzzer);
    }

  }

  private class IdentityMulDivOne extends AbstractIdentityTransformation {

    private IdentityMulDivOne() {
      super(BasicType.allNumericTypesExceptNonSquareMatrices(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // expr * 1
      // or
      // 1 * expr
      // or
      // expr / 1
      assert type.getElementType() != BasicType.BOOL;
      final BinOp operator = generator.nextBoolean() ? BinOp.MUL : BinOp.DIV;
      final Expr opaqueOne =
          (operator == BinOp.MUL && BasicType.allSquareMatrixTypes().contains(type))
              ? makeOpaqueIdentityMatrix(type, constContext, depth, fuzzer)
              : makeOpaqueOne(type, constContext, depth, fuzzer);
      return applyBinaryIdentityFunction(expr, opaqueOne, operator,
          operator == BinOp.MUL, type, constContext,
          depth, fuzzer);
    }

  }

  private class IdentityAndTrue extends AbstractIdentityTransformation {

    private IdentityAndTrue() {
      super(Arrays.asList(BasicType.BOOL), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // expr && true
      // or
      // true && expr
      assert type == BasicType.BOOL;
      return applyBinaryIdentityFunction(expr,
          makeOpaqueBoolean(true, type, constContext, depth, fuzzer), BinOp.LAND,
          true, type, constContext,
          depth, fuzzer);
    }

  }

  private class IdentityOrFalse extends AbstractIdentityTransformation {

    private IdentityOrFalse() {
      super(Arrays.asList(BasicType.BOOL), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // expr || false
      // or
      // false || expr
      assert type == BasicType.BOOL;
      return applyBinaryIdentityFunction(expr,
          makeOpaqueBoolean(false, type, constContext, depth, fuzzer), BinOp.LOR,
          true, type, constContext,
          depth, fuzzer);
    }

  }

  private class IdentityNotNot extends AbstractIdentityTransformation {

    private IdentityNotNot() {
      super(Arrays.asList(BasicType.BOOL), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // !!expr
      assert type == BasicType.BOOL;

      // Negate once
      Expr result = new UnaryExpr(new ParenExpr(applyIdentityFunction(expr, type,
          constContext,
          depth, fuzzer)), UnOp.LNOT);
      // Negate again
      result = new UnaryExpr(new ParenExpr(applyIdentityFunction(result, type, constContext,
          depth, fuzzer)), UnOp.LNOT);
      return identityConstructor(expr, result);
    }

  }

  private class IdentityTernary extends AbstractIdentityTransformation {

    private IdentityTernary() {
      super(BasicType.allScalarTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // (false ? whatever : expr)
      // or
      // (true  ? expr : whatever)
      assert BasicType.allScalarTypes().contains(type);
      // Only generate ternary expressions for scalars; the vector case causes massive blow-up
      Expr exprWithIdentityApplied = applyIdentityFunction(expr, type, constContext, depth, fuzzer);
      Expr something = fuzzedConstructor(fuzzer.fuzzExpr(type, false, constContext, depth));
      if (generator.nextBoolean()) {
        return identityConstructor(expr,
            ternary(makeOpaqueBoolean(false, BasicType.BOOL, constContext, depth, fuzzer),
                something,
                exprWithIdentityApplied));
      }
      return identityConstructor(expr,
          ternary(makeOpaqueBoolean(true, BasicType.BOOL, constContext, depth, fuzzer),
              exprWithIdentityApplied,
              something));
    }

  }

  private class IdentityMin extends AbstractIdentityTransformation {

    private IdentityMin() {
      super(numericTypesOrGenTypesIfEssl100(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // min(expr, expr)
      assert BasicType.allNumericTypes().contains(type);
      return identityConstructor(
          expr,
          new FunctionCallExpr("min",
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer),
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)));
    }

  }

  private class IdentityMax extends AbstractIdentityTransformation {

    private IdentityMax() {
      super(numericTypesOrGenTypesIfEssl100(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // max(expr, expr)
      assert BasicType.allNumericTypes().contains(type);
      return identityConstructor(
          expr,
          new FunctionCallExpr("max",
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer),
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)));
    }

  }

  private class IdentityClamp extends AbstractIdentityTransformation {

    private IdentityClamp() {
      super(numericTypesOrGenTypesIfEssl100(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // clamp(expr, expr, expr)
      assert BasicType.allNumericTypes().contains(type);
      return identityConstructor(
          expr,
          new FunctionCallExpr("clamp",
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer),
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer),
              applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)));
    }

  }

  private class IdentityMixBvec extends AbstractIdentityTransformation {

    private IdentityMixBvec() {
      super(BasicType.allGenTypes(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // mix(parts_of_expr, parts_of_expr, special_bvec)
      assert BasicType.allGenTypes().contains(type);
      final List<Expr> xElements = new ArrayList<>();
      final List<Expr> yElements = new ArrayList<>();
      final List<Expr> aElements = new ArrayList<>();

      // To avoid exponential fan-out, we only recursively apply an identity function to
      // one child.
      final int indexToWhichIdentityShouldBeApplied
          = generator.nextInt(type.getNumElements());

      for (int i = 0; i < type.getNumElements(); i++) {
        final boolean decision = generator.nextBoolean();
        aElements.add(makeOpaqueBoolean(decision, BasicType.BOOL, constContext, depth, fuzzer));
        // When we generate this random sub-expression we set constContext to true as we want to
        // ensure there are no side-effects (we must assume that it can be evaluated).
        final Expr something = fuzzedConstructor(fuzzer.fuzzExpr(type.getElementType(),
            false, true, depth));
        Expr index = type.isVector()
            ? new ArrayIndexExpr(
            new ParenExpr(expr).clone(), new IntConstantExpr(new Integer(i).toString()))
            : expr.clone();

        if (i == indexToWhichIdentityShouldBeApplied) {
          index = applyIdentityFunction(index, type.getElementType(), constContext, depth, fuzzer);
        }

        xElements.add(decision ? something : index);
        yElements.add(decision ? index : something);
      }
      return identityConstructor(
          expr,
          new FunctionCallExpr("mix",
              new TypeConstructorExpr(type.toString(), xElements),
              new TypeConstructorExpr(type.toString(), yElements),
              new TypeConstructorExpr(
                  BasicType.makeVectorType(BasicType.BOOL, type.getNumElements())
                      .toString(), aElements)));
    }

  }

}
