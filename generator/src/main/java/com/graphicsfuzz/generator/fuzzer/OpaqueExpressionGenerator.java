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
    this.expressionIdentities = new ArrayList<>();
    this.shadingLanguageVersion = shadingLanguageVersion;

    // TODO: there are many more identities that we can easily play with here
    expressionIdentities.add(new IdentityAddSubZero());
    expressionIdentities.add(new IdentityMulDivOne());
    expressionIdentities.add(new IdentityAndTrue());
    expressionIdentities.add(new IdentityOrFalse());
    expressionIdentities.add(new IdentityLogicalNotNot());
    expressionIdentities.add(new IdentityTernary());

    if (shadingLanguageVersion.supportedBitwiseOperations()) {
      expressionIdentities.add(new IdentityBitwiseNotNot());
      expressionIdentities.add(new IdentityBitwiseOrSelf());
      expressionIdentities.add(new IdentityBitwiseOrZero());
      expressionIdentities.add(new IdentityBitwiseXorZero());
      expressionIdentities.add(new IdentityBitwiseShiftZero());
    }

    expressionIdentities.add(new IdentityMin());
    expressionIdentities.add(new IdentityMax());
    expressionIdentities.add(new IdentityClamp());

    expressionIdentities.add(new IdentityRewriteComposite());

    if (shadingLanguageVersion.supportedMixNonfloatBool()) {
      expressionIdentities.add(new IdentityMixBvec());
    }

  }

  private List<OpaqueZeroOneFactory> waysToMakeZeroOrOne() {
    return Arrays.asList(
        this::opaqueZeroOrOneFromIdentityFunction,
        this::opaqueZeroOrOneFromInjectionSwitch,
        this::opaqueZeroOrOneSquareRoot,
        this::opaqueZeroOrOneAbsolute,
        this::opaqueZeroOrOneBitwiseShift,
        this::opaqueZeroOrOneBitwiseOp
    );
  }

  private List<OpaqueZeroOneFactory> waysToMakeZero() {
    List<OpaqueZeroOneFactory> opaqueZeroFactories = new ArrayList<>();
    opaqueZeroFactories.addAll(waysToMakeZeroOrOne());
    opaqueZeroFactories.add(this::opaqueZeroSin);
    opaqueZeroFactories.add(this::opaqueZeroLogarithm);
    opaqueZeroFactories.add(this::opaqueZeroTan);
    opaqueZeroFactories.add(this::opaqueZeroVectorLength);
    return opaqueZeroFactories;
  }

  private List<OpaqueZeroOneFactory> waysToMakeOne() {
    List<OpaqueZeroOneFactory> opaqueOneFactories = new ArrayList<>();
    opaqueOneFactories.addAll(waysToMakeZeroOrOne());
    opaqueOneFactories.add(this::opaqueOneExponential);
    opaqueOneFactories.add(this::opaqueOneCosine);
    opaqueOneFactories.add(this::opaqueOneNormalizedVectorLength);
    return opaqueOneFactories;
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

  /**
   * Function to generate an opaque zero or one by bitwise shifting left or right by an amount of
   * bits dependent on which opaque is being generated.
   * The minimum precision for a lowp integer in GLSL is 9 bits, with one of those bits reserved as
   * a sign bit if the integer is signed. The OpenGL specification does not define behavior for
   * shifting an integer beyond its maximum size in bits.
   * This influences the process of generating opaque values such that:
   *     If we're generating an opaque zero, we don't have to worry about losing bits, so our
   *     maximum shift value is 8 bits.
   *     If we're generating an opaque one, we have to make sure that we don't lose our 1 bit.
   *     This limits our maximum shift value to 7 bits (more on how we shift an opaque one below).
   * Possibilities for generating an opaque zero include:
   *     Shifting an opaque zero by m bits: (opaque zero) >> n or (opaque zero) << n, where n is
   *     an expression of a clamped value between 0 and m, inclusive:
   *     n = clamp(fuzzedexpr, (opaque zero), identity(typeconstructor(m)), and m is an integer
   *     between 0 and 8, inclusive.
   * Possibilities for generating an opaque zero include:
   *     Shifting an opaque one to the left by n bits, then shifting it to the right by n bits:
   *     ((opaque one) << n) >> n, where n is an expression of a clamped value between 0 and m,
   *     inclusive: n = clamp(fuzzedexpr, (opaque zero), identity(typeconstructor(m)), and m is an
   *     integer between 0 and 7, inclusive.
   *
   * @param type - the base type of the opaque value being created.
   * @param constContext - true if we're in a constant expression context, false otherwise.
   * @param depth - how deep we are in the expression.
   * @param fuzzer - the fuzzer object for generating fuzzed expressions.
   * @param isZero - true if we are making an opaque zero, false otherwise.
   * @return Optional.empty() if an opaque value can't be generated, otherwise an opaque value
   *     made from bitwise shifting.
   */
  private Optional<Expr> opaqueZeroOrOneBitwiseShift(BasicType type, boolean constContext,
                                                     final int depth, Fuzzer fuzzer,
                                                     boolean isZero) {
    if (!BasicType.allIntegerTypes().contains(type)) {
      return Optional.empty();
    }
    if (!shadingLanguageVersion.supportedBitwiseOperations()) {
      return Optional.empty();
    }
    // The minimum precision for a lowp integer in GLSL is 9 bits (with one reserved for a sign
    // bit if the integer is signed) - we don't have to worry about losing information if we're
    // shifting zero, but shifting more than 8 bits may result in undefined behavior.
    final int minBitsForLowpInt = 9;
    // While we still have a hard maximum bits to shift of 8 bits, if we're shifting one, then
    // we can potentially lose information because GLSL bit shifting is not circular. To remedy
    // that, we make sure not to shift our 1 bit out of the integer by limiting our maximum shift
    // to 7 bits.
    final int minBitsForLowpUnsignedInt = minBitsForLowpInt - 1;
    final int maxValue = generator.nextInt(isZero ? minBitsForLowpInt : minBitsForLowpUnsignedInt);
    // We pass true as constContext when fuzzing here because the expression will be evaluated,
    // so we don't want any side effects.
    final Expr shiftValue = makeClampedFuzzedExpr(type, constContext, depth, fuzzer, maxValue);
    if (isZero) {
      final BinOp operator = generator.nextBoolean() ? BinOp.SHL : BinOp.SHR;
      return Optional.of(
          new ParenExpr(
              new BinaryExpr(
                  makeOpaqueZero(type, constContext, depth, fuzzer),
                  shiftValue,
                  operator)));
    } else {
      // We're going to shift twice in opposite directions by the same value.
      final Expr shiftBackValue = generator.nextBoolean() ? shiftValue.clone()
          : makeClampedFuzzedExpr(type, constContext, depth, fuzzer, maxValue);
      return Optional.of(
          new ParenExpr(
              new BinaryExpr(
                  new ParenExpr(
                      new BinaryExpr(
                          makeOpaqueOne(type, constContext, depth, fuzzer),
                          shiftValue,
                          BinOp.SHL)),
                  shiftBackValue,
                  BinOp.SHR)));
    }
  }

  /**
   * Utility function to clamp a fuzzed expression between an opaque zero and the identity of a
   * type constructor of the given type, with the value of the bound argument. Note that this
   * function only supports integer types currently - it could be extended to support floating
   * point numbers as well if needed. Another note is that this function does not check its bound
   * for validity - specifying a bound larger than 256 or a negative value could cause invalid GLSL
   * to be generated depending on the precision and type of the integer.
   *
   * @param type - the type to make a clamped expression from.
   * @param bound - the upper bound for the clamped expression.
   * @return an expression of a clamped value between 0 and bound, inclusive:
   *     clamp(fuzzedexpr, (opaque zero), identity(typeconstructor(bound)),
   */
  private Expr makeClampedFuzzedExpr(BasicType type, boolean constContext,
                                     final int depth, Fuzzer fuzzer, int bound) {
    assert BasicType.allIntegerTypes().contains(type);
    return new FunctionCallExpr(
        "clamp",
        fuzzer.fuzzExpr(type, false, true, depth),
        makeOpaqueZero(type, constContext, depth, fuzzer),
        applyIdentityFunction(
            new TypeConstructorExpr(
                type.toString(),
                type.getElementType() == BasicType.INT
                    ? new IntConstantExpr(String.valueOf(bound))
                    : new UIntConstantExpr(bound + "u")),
            type, constContext, depth, fuzzer));
  }

  /**
   * Function to generate an opaque value by performing a bitwise operation on an opaque zero or
   * an opaque one.
   * Possibilities for generating an opaque zero include:
   * Bitwise ANDing a fuzzed expression with an opaque zero: (fuzzedexpr) & (opaque zero)
   * Bitwise ORing an opaque zero with an opaque zero: (opaque zero) | (opaque zero)
   * Bitwise XORing an opaque zero with an opaque zero or an opaque one with an opaque one:
   *     (opaque zero) ^ (opaque zero) or (opaque one) ^ (opaque one)
   * Possibilities for generating an opaque one include:
   * Bitwise ANDing an opaque one with an opaque one: (opaque one) & (opaque one)
   * Bitwise ORing an opaque one with an opaque zero or one: (opaque one) | (opaque zero or one)
   * Bitwise XORing an opaque zero with an opaque one or an opaque one with an opaque zero:
   *     (opaque zero) ^ (opaque one) or (opaque one) ^ (opaque zero)
   *
   * @param type - the base type of the opaque value being created.
   * @param constContext - true if we're in a constant expression context, false otherwise.
   * @param depth - how deep we are in the expression.
   * @param fuzzer - the fuzzer object for generating fuzzed expressions.
   * @param isZero - true if we are making an opaque zero, false otherwise.
   * @return Optional.empty() if an opaque value can't be generated, otherwise an opaque value
   *     made from performing a bitwise operation on an opaque zero or opaque one.
   */
  private Optional<Expr> opaqueZeroOrOneBitwiseOp(BasicType type, boolean constContext,
                                                   final int depth, Fuzzer fuzzer, boolean isZero) {
    if (!BasicType.allIntegerTypes().contains(type)) {
      return Optional.empty();
    }
    if (!shadingLanguageVersion.supportedBitwiseOperations()) {
      return Optional.empty();
    }
    final int numPossibleOperators = 3;
    final int operator = generator.nextInt(numPossibleOperators);
    Optional<Expr> opaqueExpr;
    switch (operator) {
      case 0:
        // Bitwise AND
        if (isZero) {
          // We pass true as constContext when fuzzing here because the expression will be
          // evaluated, so we don't want any side effects.
          final Expr fuzzedExpr = fuzzer.fuzzExpr(type, false, true, depth);
          final Expr opaqueZero = makeOpaqueZero(type, constContext, depth, fuzzer);
          opaqueExpr = Optional.of(
              new ParenExpr(
                  generator.nextBoolean()
                  ? new BinaryExpr(fuzzedExpr, opaqueZero, BinOp.BAND)
                  : new BinaryExpr(opaqueZero, fuzzedExpr, BinOp.BAND)));
        } else {
          opaqueExpr = Optional.of(
              new ParenExpr(
                  new BinaryExpr(
                      makeOpaqueOne(type, constContext, depth, fuzzer),
                      makeOpaqueOne(type, constContext, depth, fuzzer),
                      BinOp.BAND)));
        }
        break;
      case 1:
        // Bitwise OR
        if (isZero) {
          opaqueExpr = Optional.of(
              new ParenExpr(
                  new BinaryExpr(
                      makeOpaqueZero(type, constContext, depth, fuzzer),
                      makeOpaqueZero(type, constContext, depth, fuzzer),
                      BinOp.BOR)));
        } else {
          final Expr opaqueOne = makeOpaqueOne(type, constContext, depth, fuzzer);
          final Expr opaqueZeroOrOne = makeOpaqueZeroOrOne(generator.nextBoolean(), type,
              constContext, depth, fuzzer);
          opaqueExpr = Optional.of(
              new ParenExpr(
                  generator.nextBoolean()
                  ? new BinaryExpr(opaqueOne, opaqueZeroOrOne, BinOp.BOR)
                  : new BinaryExpr(opaqueZeroOrOne, opaqueOne, BinOp.BOR)));
        }
        break;
      default:
        // Bitwise XOR
        assert operator == numPossibleOperators - 1;
        boolean useZeroOrOne = generator.nextBoolean();
        opaqueExpr = Optional.of(
            new ParenExpr(
                isZero
                ? new BinaryExpr(
                    makeOpaqueZeroOrOne(useZeroOrOne, type, constContext, depth, fuzzer),
                    makeOpaqueZeroOrOne(useZeroOrOne, type, constContext, depth, fuzzer),
                    BinOp.BXOR)
                : new BinaryExpr(
                    makeOpaqueZeroOrOne(useZeroOrOne, type, constContext, depth, fuzzer),
                    makeOpaqueZeroOrOne(!useZeroOrOne, type, constContext, depth, fuzzer),
                    BinOp.BXOR)));
    }
    return opaqueExpr;
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
    public boolean preconditionHolds(Expr expr, BasicType basicType) {
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

  private class IdentityLogicalNotNot extends AbstractIdentityTransformation {

    private IdentityLogicalNotNot() {
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
      super(BasicType.allNumericTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // (false ? whatever : expr)
      // or
      // (true  ? expr : whatever)
      assert BasicType.allNumericTypes().contains(type);
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

  /**
   * Identity transformation for integer types (both unsigned and signed, and their vectors) that
   * double bitwise inverts an integer, producing the same integer as output. When performed,
   * transforms an expression, e, such that:
   *    e -> ~(~(expr)).
   */
  private class IdentityBitwiseNotNot extends AbstractIdentityTransformation {
    private IdentityBitwiseNotNot() {
      super(BasicType.allIntegerTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert BasicType.allIntegerTypes().contains(type);
      // Invert once
      Expr result = new UnaryExpr(new ParenExpr(applyIdentityFunction(expr, type,
          constContext,
          depth, fuzzer)), UnOp.BNEG);
      // Invert again
      result = new UnaryExpr(new ParenExpr(applyIdentityFunction(result, type, constContext,
          depth, fuzzer)), UnOp.BNEG);
      return identityConstructor(expr, result);
    }
  }

  /**
   * Identity transformation for integer types (both unsigned and signed, and their vectors) that
   * ORs an integer with itself, producing the same integer as output. This identity requires
   * expressions to be side effect free because the same mutated expression is evaluated twice.
   * When performed, transforms an expression, e, such that:
   *    e -> (e) | (e).
   */
  private class IdentityBitwiseOrSelf extends AbstractIdentityTransformation {
    private IdentityBitwiseOrSelf() {
      super(BasicType.allIntegerTypes(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert BasicType.allIntegerTypes().contains(type);
      // We use parentheses to prevent issues with order of operations in ternary expressions.
      return identityConstructor(
          expr,
          new BinaryExpr(
              new ParenExpr(applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)),
              new ParenExpr(applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)),
                  BinOp.BOR));
    }
  }
  
  /**
   * Identity transformation for integer types (both unsigned and signed, and their vectors) that
   * ORs an integer with zero, producing the same integer as output.
   * When performed, transforms an expression, e, such that:
   *    e -> (e) | (opaque 0) or e -> (opaque 0) | (e).
   */
  private class IdentityBitwiseOrZero extends AbstractIdentityTransformation {
    private IdentityBitwiseOrZero() {
      super(BasicType.allIntegerTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert BasicType.allIntegerTypes().contains(type);
      return applyBinaryIdentityFunction(
          expr.clone(),
          makeOpaqueZero(type, constContext, depth, fuzzer),
          BinOp.BOR, true, type, constContext, depth, fuzzer);
    }
  }

  /**
   * Identity transformation for integer types (both unsigned and signed, and their vectors) that
   * XORs an integer with zero, producing the same integer as output. When performed, transforms an
   * expression, e, such that:
   *    e -> (e) ^ (opaque 0) or e -> (opaque 0) ^ (e).
   */
  private class IdentityBitwiseXorZero extends AbstractIdentityTransformation {
    private IdentityBitwiseXorZero() {
      super(BasicType.allIntegerTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert BasicType.allIntegerTypes().contains(type);
      return applyBinaryIdentityFunction(
          expr.clone(),
          makeOpaqueZero(type, constContext, depth, fuzzer),
          BinOp.BXOR, true, type, constContext, depth, fuzzer);
    }
  }

  /**
   * Identity transformation for integer types (both unsigned and signed, and their vectors) that
   * shifts an integer by zero. When performed, transforms an expression, e, such that:
   *    e -> (e) >> (opaque 0) or e -> (e) << (opaque 0)
   */
  private class IdentityBitwiseShiftZero extends AbstractIdentityTransformation {
    private IdentityBitwiseShiftZero() {
      super(BasicType.allIntegerTypes(), false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert BasicType.allIntegerTypes().contains(type);
      final BinOp operator = generator.nextBoolean() ? BinOp.SHL : BinOp.SHR;
      return applyBinaryIdentityFunction(
          expr.clone(),
          makeOpaqueZero(type, constContext, depth, fuzzer),
          operator, false, type, constContext, depth, fuzzer);
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

  private class IdentityRewriteComposite extends AbstractIdentityTransformation {
    private IdentityRewriteComposite() {
      // all non-boolean vector/matrix types
      super(BasicType.allNumericTypes().stream().filter(
          item -> !item.isScalar()).collect(Collectors.toList()),
          false);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      // v -> (true ? vecX(..., identity(v[Y]), ...) : _)
      // v -> (false ? _ : vecX(..., identity(v[Y]), ...))
      // where v is a vector of size X, y is the random entry in v we want to apply
      // identities to, and ... is the other entries in v that we don't change.
      // Similarly for matrices.

      assert type.isVector() || type.isMatrix();
      assert expr instanceof VariableIdentifierExpr;

      final int numColumns = type.isVector() ? type.getNumElements() : type.getNumColumns();
      final int columnToFurtherTransform = generator.nextInt(numColumns);
      final List<Expr> typeConstructorArguments = new ArrayList<>();

      if (type.isMatrix() && generator.nextBoolean()) {
        // Further break down matrix constructor into single components, to increase diversity.
        // For example, a 2x2 matrix may become mat2(m[0][0], m[0][1], IDENTITY(m[1][0]), m[1][1]).
        // GLSL's matrix notation follows column-major indexing rules.
        final int rowToFurtherTransform = generator.nextInt(type.getNumRows());
        for (int i = 0; i < numColumns; i++) {
          for (int j = 0; j < type.getNumRows(); j++) {
            // The inner ArrayIndexExpr is the column (first) index, while the outer ArrayIndexExpr
            // is the row (second) index.
            Expr argument = new ArrayIndexExpr(new ArrayIndexExpr(expr.clone(),
                new IntConstantExpr(String.valueOf(i)).clone()),
                new IntConstantExpr(String.valueOf(j)));
            if (i == columnToFurtherTransform && j == rowToFurtherTransform) {
              argument = applyIdentityFunction(argument, type.getElementType(), constContext,
                  depth, fuzzer);
            }
            typeConstructorArguments.add(argument);
          }
        }
      } else {
        // Create a constructor with columns only (or single entries, in the case of vectors).
        // A 2x2 matrix may become mat2(m[0], IDENTITY(m[1])).
        // A vec4 may become vec4(v[0], v[1], IDENTITY(v[2]), v[3]).
        for (int i = 0; i < numColumns; i++) {
          Expr argument = new ArrayIndexExpr(expr.clone(), new IntConstantExpr(String.valueOf(i)));
          if (i == columnToFurtherTransform) {
            argument = applyIdentityFunction(argument,
                type.isVector() ? type.getElementType() : type.getColumnType(),
                constContext, depth, fuzzer);
          }
          typeConstructorArguments.add(argument);
        }
      }
      return identityConstructor(expr,
          new TypeConstructorExpr(type.toString(), typeConstructorArguments));
    }

    @Override
    public boolean preconditionHolds(Expr expr, BasicType basicType) {
      return super.preconditionHolds(expr, basicType) && expr instanceof VariableIdentifierExpr;
    }
  }
}
