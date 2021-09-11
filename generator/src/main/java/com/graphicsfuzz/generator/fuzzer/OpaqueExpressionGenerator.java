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
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;
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
import java.util.stream.Stream;

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
    expressionIdentities.add(new IdentityCompositeConstructorExpansion());
    if (shadingLanguageVersion.supportedMixNonfloatBool()) {
      expressionIdentities.add(new IdentityMixBvec());
    }

    if (shadingLanguageVersion.supportedTranspose()) {
      expressionIdentities.add(new IdentityDoubleTranspose());
    }
    expressionIdentities.add(new IdentityMatrixMultIdentity());

  }

  private List<OpaqueZeroOneFactory> waysToMakeZeroOrOne() {
    return Arrays.asList(
        this::opaqueZeroOrOneFromIdentityFunction,
        this::opaqueZeroOrOneFromInjectionSwitch,
        this::opaqueZeroOrOneSquareRoot,
        this::opaqueZeroOrOneAbsolute,
        this::opaqueZeroOrOneDot,
        this::opaqueZeroOrOneBitwiseShift,
        this::opaqueZeroOrOneBitwiseOp,
        this::opaqueZeroOrOneMatrixDet
    );
  }

  /**
   * This method has non-private visibility for purposes of testing only.
   */
  List<OpaqueZeroOneFactory> waysToMakeZero() {
    List<OpaqueZeroOneFactory> opaqueZeroFactories = new ArrayList<>();
    opaqueZeroFactories.addAll(waysToMakeZeroOrOne());
    opaqueZeroFactories.add(this::opaqueZeroSin);
    opaqueZeroFactories.add(this::opaqueZeroLogarithm);
    opaqueZeroFactories.add(this::opaqueZeroTan);
    opaqueZeroFactories.add(this::opaqueZeroVectorLength);
    opaqueZeroFactories.add(this::opaqueZeroVectorCross);
    return opaqueZeroFactories;
  }

  /**
   * This method has non-private visibility for purposes of testing only.
   */
  List<OpaqueZeroOneFactory> waysToMakeOne() {
    List<OpaqueZeroOneFactory> opaqueOneFactories = new ArrayList<>();
    opaqueOneFactories.addAll(waysToMakeZeroOrOne());
    opaqueOneFactories.add(this::opaqueOneExponential);
    opaqueOneFactories.add(this::opaqueOneCosine);
    if (shadingLanguageVersion.supportedRound()) {
      opaqueOneFactories.add(this::opaqueOneRoundedNormalizedVectorLength);
    }
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
   * Function to generate an opaque zero or one from the determinant of a generated matrix.
   * This function heavily relies on the fact that a triangular matrix - a matrix that only has
   * values below the diagonal (lower triangular) or above the diagonal (upper triangular) - always
   * has a determinant that is the product of its diagonal values. For example,
   * [1 0 0]    This is a lower triangular 3x3 matrix. Its determinant is the product of its
   * [0 1 0]    diagonals, so det(mat3) = 1 * 1 * 1 = 1.
   * [1 0 1]
   * --------------------------------------
   * [0 1 0 1]    This is an upper triangular 4x4 matrix. Its determinant is the product of its
   * [0 0 1 1]    diagonals, so det(mat4) = 0 * 0 * 0 * 0 = 0.
   * [0 0 0 1]
   * [0 0 0 0]
   * --------------------------------------
   * In general, a triangular matrix is of these forms for our purpose:
   * [t x] [t 0] [t x x] [t 0 0] [t x x x] [t 0 0 0]
   * [0 t] [x t] [0 t x] [x t 0] [0 t x x] [x t 0 0]
   *             [0 0 t] [x x t] [0 0 t x] [x x t 0]
   *                             [0 0 0 t] [x x x t]
   * where x is a "modifiable index" whose value is totally irrelevant for the determinant,
   * and t is a "diagonal" that is zero if we're making an opaque zero, otherwise an opaque one.
   * For simplicity, we choose not to consider the case where the diagonal has one zero and
   * the rest ones (which would produce a determinant of zero).
   * -------------------------------------
   * There are some other things to know about this algorithm:
   * [0  1  2  3 ]     - When constructing a square matrix in GLSL, the mat constructor takes
   * [4  5  6  7 ]       matrixDimension * matrixDimension number of arguments - this diagram shows
   * [8  9  10 11]       how the matrix is constructed from those arguments.
   * [12 13 14 15]
   *  -  Notice how in the above diagram, the diagonals for a 4x4 matrix are arguments 0, 5, 10, 15.
   *     This occurs similarly for 3x3 (0, 4, 8) and 2x2 (0, 3). The sequence of constructor indices
   *     that are diagonals: [0, (matrixDim + 1), 2 * (matrixDim + 1), 3 * (matrixDim + 1)...].
   * @param type - the base type of the opaque value being created.
   * @param constContext - true if we're in a constant expression context, false otherwise.
   * @param depth - how deep we are in the expression.
   * @param fuzzer - the fuzzer object for generating fuzzed expressions.
   * @param isZero - true if we are making an opaque zero, false otherwise.
   * @return Optional.empty() if an opaque value can't be generated, otherwise an opaque value
   *     made from the determinant of a triangular matrix.
   */
  private Optional<Expr> opaqueZeroOrOneMatrixDet(BasicType type, boolean constContext,
                                                     final int depth, Fuzzer fuzzer,
                                                     boolean isZero) {
    // As discussed in https://github.com/KhronosGroup/glslang/issues/1865, the determinant function
    // is not deemed to be compile-time constant.
    if (constContext) {
      return Optional.empty();
    }
    if (type != BasicType.FLOAT) {
      return Optional.empty();
    }
    if (!shadingLanguageVersion.supportedDeterminant() || generationParams.isWgslCompatible()) {
      return Optional.empty();
    }
    // If true, we will make an upper triangular matrix - otherwise it will be lower triangular.
    final boolean isUpperTriangular = generator.nextBoolean();
    // Matrices sent to determinant() must be 2x2, 3x3, or 4x4.
    final int matrixDimension = generator.nextInt(3) + 2;
    // Indices of arguments in a matrix constructor that can be nonzero for triangular matrix,
    // excluding the diagonal of the matrix. Zero-indexed.
    List<Integer> modifiableArgs;
    switch (matrixDimension) {
      case 2:
        modifiableArgs = isUpperTriangular
            ? Arrays.asList(1)                       // mat2, upper triangular
            : Arrays.asList(2);                      // mat2, lower triangular
        break;
      case 3:
        modifiableArgs = isUpperTriangular
            ? Arrays.asList(1, 2, 5)                 // mat3, upper triangular
            : Arrays.asList(3, 6, 7);                // mat3, lower triangular
        break;
      case 4:
        modifiableArgs = isUpperTriangular
            ? Arrays.asList(1, 2, 3, 6, 7, 11)       // mat4, upper triangular
            : Arrays.asList(4, 8, 9, 12, 13, 14);    // mat4, lower triangular
        break;
      default:
        // Should not be reachable.
        throw new UnsupportedOperationException("Generated matrix dimension was out of bounds?");
    }
    final List<Expr> matrixConstructorArgs = new ArrayList<Expr>();
    // Diagonal indices are spaced by (matrixDimension + 1) - e.g. mat4 diagonals: 0, 5, 10, 15
    int nextDiagonalMatrixIndex = 0;
    for (int i = 0; i < matrixDimension * matrixDimension; i++) {
      if (i == nextDiagonalMatrixIndex) {
        // We're in a diagonal, so the value depends on if we're making an opaque zero or one.
        assert !modifiableArgs.contains(i);
        matrixConstructorArgs.add(makeOpaqueZeroOrOne(isZero, type, constContext, depth, fuzzer));
        nextDiagonalMatrixIndex += matrixDimension + 1;
      } else if (modifiableArgs.contains(i)) {
        // We're in a modifiable index - the value doesn't matter for the determinant.
        matrixConstructorArgs.add(makeOpaqueZeroOrOne(
            generator.nextBoolean(), type, constContext, depth, fuzzer));
      } else {
        // We're in a non-modifiable index - we need zero or we'll violate the triangular
        // property of the matrix.
        matrixConstructorArgs.add(makeOpaqueZero(type, constContext, depth, fuzzer));
      }
    }
    assert matrixConstructorArgs.size() == matrixDimension * matrixDimension;
    return Optional.of(
        new FunctionCallExpr("determinant",
            new TypeConstructorExpr(
                BasicType.makeMatrixType(matrixDimension, matrixDimension).toString(),
                matrixConstructorArgs)));
  }

  /**
   * Function to generate an opaque zero or one by taking the dot product of two vectors. This
   * function depends on two rules of the dot product. Let u and v be vectors of the same width,
   * and let i be a valid index into those vectors.
   *     If u[i] != 0 <==> v[i] = 0 for all i, then dot(u, v) = 0. Example: u = (0, 1), v = (1, 0),
   *     then dot(u, v) = 1 * 0 + 0 * 1 = 0.
   *     If u[i] = v[i] = 0 for all i except for a single index, j, and if u[j] = v[j] = 1, then
   *     dot(u, v) = 0. Example: u = (0, 1), v = (0, 1), then dot(u, v) = 0 * 0 + 1 * 1 = 1.
   * Because fuzzed expressions are not always well defined, we forgo those in favor of simply
   * using opaque ones and zeroes when generating vectors for this function.
   * @param type - the base type of the opaque value being created.
   * @param constContext - true if we're in a constant expression context, false otherwise.
   * @param depth - how deep we are in the expression.
   * @param fuzzer - the fuzzer object for generating fuzzed expressions.
   * @param isZero - true if we are making an opaque zero, false otherwise.
   * @return Optional.empty() if an opaque value can't be generated, otherwise an opaque value
   *     made from the dot product of two vectors.
   */
  private Optional<Expr> opaqueZeroOrOneDot(BasicType type, boolean constContext,
                                            final int depth, Fuzzer fuzzer, boolean isZero) {
    if (type != BasicType.FLOAT) {
      return Optional.empty();
    }
    // If width is 1, type will be float - otherwise the type will be the corresponding vector type.
    final int vectorWidth = generator.nextPositiveInt(BasicType.VEC4.getNumElements()) + 1;
    final Expr dotProductExpr;
    final List<Expr> firstVectorArgs = new ArrayList<Expr>();
    final List<Expr> secondVectorArgs = new ArrayList<Expr>();
    if (isZero) {
      // We're basically producing inverse vectors: u[i] = 0 <==> v[i] != 0.
      for (int i = 0; i < vectorWidth; i++) {
        final boolean firstVectorArgIsZero = generator.nextBoolean();
        firstVectorArgs.add(makeOpaqueZeroOrOne(
            firstVectorArgIsZero, type, constContext, depth, fuzzer));
        secondVectorArgs.add(makeOpaqueZeroOrOne(
            !firstVectorArgIsZero, type, constContext, depth, fuzzer));
      }
    } else {
      // The two vectors will be exactly the same - all 0 except for one value, which will be 1.
      final int oneIndex = generator.nextInt(vectorWidth);
      for (int i = 0; i < vectorWidth; i++) {
        firstVectorArgs.add(
            makeOpaqueZeroOrOne(i != oneIndex, type, constContext, depth, fuzzer));
        secondVectorArgs.add(
            makeOpaqueZeroOrOne(i != oneIndex, type, constContext, depth, fuzzer));
      }
    }
    assert firstVectorArgs.size() == vectorWidth && secondVectorArgs.size() == vectorWidth;
    dotProductExpr = new FunctionCallExpr("dot",
        new TypeConstructorExpr(
            BasicType.makeVectorType(type, vectorWidth).toString(), firstVectorArgs),
        new TypeConstructorExpr(
            BasicType.makeVectorType(type, vectorWidth).toString(), secondVectorArgs));
    return Optional.of(
        identityConstructor(
            dotProductExpr,
            applyIdentityFunction(dotProductExpr.clone(), type, constContext, depth, fuzzer)));
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
   *     an integer in [0, 8].
   * Possibilities for generating an opaque zero include:
   *     Shifting an opaque one to the left by n bits, then shifting it to the right by n bits:
   *     ((opaque one) << n) >> n, where n is an integer in [0, 7].
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
    final int shiftValueConstant = generator.nextInt(
        isZero ? minBitsForLowpInt : minBitsForLowpUnsignedInt);
    final Expr shiftValueConstructor =
        new TypeConstructorExpr(type.toString(),
            BasicType.allUnsignedTypes().contains(type)
            ? new UIntConstantExpr(String.valueOf(shiftValueConstant) + 'u')
            : new IntConstantExpr(String.valueOf(shiftValueConstant)));
    final Expr shiftValueWithIdentityApplied = identityConstructor(
        shiftValueConstructor,
        applyIdentityFunction(shiftValueConstructor, type, constContext, depth, fuzzer));
    if (isZero) {
      final BinOp operator = generator.nextBoolean() ? BinOp.SHL : BinOp.SHR;
      return Optional.of(
          new ParenExpr(
              new BinaryExpr(
                  makeOpaqueZero(type, constContext, depth, fuzzer),
                  shiftValueWithIdentityApplied,
                  operator)));
    } else {
      // We're going to shift twice in opposite directions by the same value.
      final Expr backShiftValueWithIdentityApplied = identityConstructor(
          shiftValueConstructor.clone(),
          applyIdentityFunction(shiftValueConstructor.clone(), type, constContext, depth, fuzzer));
      return Optional.of(
          new ParenExpr(
              new BinaryExpr(
                  new ParenExpr(
                      new BinaryExpr(
                          makeOpaqueOne(type, constContext, depth, fuzzer),
                          shiftValueWithIdentityApplied,
                          BinOp.SHL)),
                  backShiftValueWithIdentityApplied,
                  BinOp.SHR)));
    }
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
    if (type != BasicType.FLOAT) {
      // 'length' has return type 'float', so we can only create a scalar floating-point zero.
      return Optional.empty();
    }

    // We can choose any vector size we wish for the vector whose length will be computed.
    final BasicType vectorType =
        BasicType.allGenTypes().get(generator.nextInt(BasicType.allGenTypes().size()));

    return Optional.of(new FunctionCallExpr("length", makeOpaqueZero(vectorType, constContext,
        depth,
        fuzzer)));
  }

  /**
   * Function to create an opaque zero (specifically an opaque zero vec3) by taking the cross
   * product of two equivalent vec3s. This opaque function relies on the fact that if u == v, where
   * u and v are vec3, then cross(u, v) = (0, 0, 0). These equivalent vec3s are produced by
   * fuzzing random float values and applying different identity functions to them per vector.
   * @param type - the base type of the opaque value being created.
   * @param constContext - true if we're in a constant expression context, false otherwise.
   * @param depth - how deep we are in the expression.
   * @param fuzzer - the fuzzer object for generating fuzzed expressions.
   * @param isZero - true if we are making an opaque zero, false otherwise.
   * @return Optional.empty() if an opaque value can't be generated, otherwise an opaque zero vec3
   *     made from the cross product of two equal vec3s.
   */
  private Optional<Expr> opaqueZeroVectorCross(BasicType type, boolean constContext,
                                               final int depth,
                                               Fuzzer fuzzer, boolean isZero) {
    assert isZero;
    if (type != BasicType.VEC3) {
      // Cross product only works on vec3 and can only produce vec3 as a return type.
      return Optional.empty();
    }
    // We only want literals, so we need to make our own LiteralFuzzer rather than use the supplied
    // Fuzzer object.
    final LiteralFuzzer litFuzzer = new LiteralFuzzer(generator);
    // cross(vec3(firstVec3ConstructorArgs), vec3(secondVec3ConstructorArgs))
    final List<Expr> firstVec3ConstructorArgs = new ArrayList<Expr>();
    final List<Expr> secondVec3ConstructorArgs = new ArrayList<Expr>();
    for (int i = 0; i < type.getNumElements(); i++) {
      final Optional<Expr> maybeFuzzedFloatLiteral = litFuzzer.fuzz(type.getElementType());
      // Something went horribly wrong if we got an Optional.empty() - we are guaranteed to obtain a
      // fuzzed expression, since the element type of a vec3 is a float, and it is always possible
      // to fuzz a float literal.
      assert maybeFuzzedFloatLiteral.isPresent();
      final Expr fuzzedFloatLiteral = maybeFuzzedFloatLiteral.get();
      // We apply different identities to the literals per vector - the two vectors are
      // semantically the same vector (and so cross() should still return the zero vector).
      firstVec3ConstructorArgs.add(
          applyIdentityFunction(
              fuzzedFloatLiteral, type.getElementType(), constContext, depth, fuzzer));
      secondVec3ConstructorArgs.add(
          applyIdentityFunction(
              fuzzedFloatLiteral.clone(), type.getElementType(), constContext, depth, fuzzer));
    }
    return Optional.of(
        new FunctionCallExpr("cross",
            new TypeConstructorExpr(type.toString(), firstVec3ConstructorArgs),
            new TypeConstructorExpr(type.toString(), secondVec3ConstructorArgs)));
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

  private Optional<Expr> opaqueOneRoundedNormalizedVectorLength(BasicType type,
                                                                boolean constContext,
                                                                final int depth,
                                                                Fuzzer fuzzer,
                                                                boolean isZero) {
    // represent 1 as the rounded length of a normalized vector
    assert !isZero;
    if (type != BasicType.FLOAT) {
      // 'length' has return type 'float', so we can only create a scalar floating-point zero.
      return Optional.empty();
    }

    // We can choose any vector size we wish for the vector whose length will be computed.
    final BasicType vectorType =
        BasicType.allGenTypes().get(generator.nextInt(BasicType.allGenTypes().size()));

    // We create a vector of ones and normalize it, rounding the result to guard against the case
    // where round-off leads to a result that is not quite one.  Note that we could be more general
    // here and normalize any non-zero vector.
    return Optional.of(new FunctionCallExpr("round",
        new FunctionCallExpr("length",
            new FunctionCallExpr("normalize",
                makeOpaqueZeroOrOne(false, vectorType, constContext, depth, fuzzer)))));
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
            .filter(item -> item.preconditionHolds(expr, type, constContext))
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
    return macroConstructor(Constants.GLF_IDENTITY, addParenthesesIfCommaExpr(original.clone()),
        withIdentityApplied);
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
    public boolean preconditionHolds(Expr expr, BasicType basicType, boolean constContext) {
      if (!acceptableTypes.contains(basicType)) {
        return false;
      }
      if (!exprMustBeSideEffectFree) {
        return true;
      }
      return SideEffectChecker.isSideEffectFree(expr, shadingLanguageVersion,
          generationParams.getShaderKind());

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
                addParenthesesIfCommaExpr(something),
                addParenthesesIfCommaExpr(exprWithIdentityApplied)));
      }
      return identityConstructor(expr,
          ternary(makeOpaqueBoolean(true, BasicType.BOOL, constContext, depth, fuzzer),
              addParenthesesIfCommaExpr(exprWithIdentityApplied),
              addParenthesesIfCommaExpr(something)));
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
      // We cannot pass a comma expression as an argument to 'min' (as it would look like two
      // arguments), so add parentheses if needed.
      final Expr exprWithParenthesesIfNeeded = addParenthesesIfCommaExpr(expr);
      return identityConstructor(
          expr,
          new FunctionCallExpr("min",
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer),
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer)));
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
      // We cannot pass a comma expression as an argument to 'max' (as it would look like two
      // arguments), so add parentheses if needed.
      final Expr exprWithParenthesesIfNeeded = addParenthesesIfCommaExpr(expr);
      return identityConstructor(
          expr,
          new FunctionCallExpr("max",
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer),
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer)));
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
      // We cannot pass a comma expression as an argument to 'clamp' (as it would look like two
      // arguments), so add parentheses if needed.
      final Expr exprWithParenthesesIfNeeded = addParenthesesIfCommaExpr(expr);
      return identityConstructor(
          expr,
          new FunctionCallExpr("clamp",
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer),
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer),
              applyIdentityFunction(exprWithParenthesesIfNeeded.clone(), type, constContext, depth,
                  fuzzer)));
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

        // Avoid top-level occurrences of the comma operator as type constructor parameters.
        xElements.add(addParenthesesIfCommaExpr(decision ? something : index));
        yElements.add(addParenthesesIfCommaExpr(decision ? index : something));
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
    public boolean preconditionHolds(Expr expr, BasicType basicType, boolean constContext) {
      return super.preconditionHolds(expr, basicType, constContext)
          && expr instanceof VariableIdentifierExpr;
    }
  }

  /**
   * Identity transformation to insert an expression into a wider vector or matrix using a
   * type constructor, then extract it back out again using another type constructor. When
   * performed, transforms an expression e of type t to identity(t(identity(m(identity(e), ..)))),
   * where m is a type of equal or greater width than t.
   * The rules for this smaller -> larger -> smaller transformation are as follows:
   *     If the given type is a vector or scalar, the inner constructor type can be a vector of the
   *     same element type with equal/greater width than the given type, or (provided that the
   *     given type is a floating point genType) it can be a matrix with equal/greater column
   *     width than the given type.
   *     If the given type is a matrix, the inner constructor type can be a matrix with
   *     equal/greater column/row width than the given type.
   */
  private class IdentityCompositeConstructorExpansion extends AbstractIdentityTransformation {
    private IdentityCompositeConstructorExpansion() {
      super(BasicType.allBasicTypes().stream()
          .filter(item -> !Arrays.asList(BasicType.BVEC4, BasicType.IVEC4, BasicType.UVEC4,
              BasicType.MAT4X4)
              .contains(item))
          .collect(Collectors.toList()), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert !Arrays.asList(BasicType.BVEC4, BasicType.IVEC4, BasicType.UVEC4, BasicType.MAT4X4)
          .contains(type);
      final List<BasicType> innerConstructorTypes = new ArrayList<BasicType>();
      // Our inner constructor type will be equal or larger in width than our given type. The goal
      // of the next set of conditionals is to populate this list with all types that can fit our
      // given type.
      final int maxVectorSize = 4;
      if (!type.isMatrix()) {
        for (int i = type.getNumElements(); i <= maxVectorSize; i++) {
          innerConstructorTypes.add(BasicType.makeVectorType(type.getElementType(), i));
        }
        if (type.getElementType() == BasicType.FLOAT) {
          innerConstructorTypes.addAll(BasicType.allSquareMatrixTypes());
          if (shadingLanguageVersion.supportedNonSquareMatrices()) {
            innerConstructorTypes.addAll(BasicType.allNonSquareMatrixTypes());
          }
        }
      } else {
        for (BasicType constructorType : BasicType.allMatrixTypes()) {
          if (type.getNumRows() <= constructorType.getNumRows()
              && type.getNumColumns() <= constructorType.getNumColumns()) {
            if (BasicType.allSquareMatrixTypes().contains(constructorType)) {
              innerConstructorTypes.add(constructorType);
            } else if (shadingLanguageVersion.supportedNonSquareMatrices()) {
              innerConstructorTypes.add(constructorType);
            }
          }
        }
      }
      assert !innerConstructorTypes.isEmpty();
      assert innerConstructorTypes.contains(type);
      final BasicType randomInnerConstructorType =
          innerConstructorTypes.get(generator.nextInt(innerConstructorTypes.size()));
      final List<Expr> innerConstructorArgs = new ArrayList<>();
      // We cannot pass a comma expression as an argument to a type constructor (as it would look
      // like two arguments), so add parentheses if needed.
      innerConstructorArgs.add(
          addParenthesesIfCommaExpr(applyIdentityFunction(expr.clone(), type, constContext, depth,
              fuzzer)));
      // GLSL won't fill in the blanks of the inner constructor unless a matrix is being constructed
      // from another matrix.
      if (!type.isMatrix()) {
        final int numExcessConstructorArgs =
            randomInnerConstructorType.getNumElements() - type.getNumElements();
        for (int i = 0; i < numExcessConstructorArgs; i++) {
          // We add a boolean if the element type is boolean, and a zero/one otherwise.
          innerConstructorArgs.add(
              type.getElementType() == BasicType.BOOL
                  ? makeOpaqueBoolean(generator.nextBoolean(),
                                      BasicType.BOOL,
                                      constContext,
                                      depth,
                                      fuzzer)
                  : makeOpaqueZeroOrOne(generator.nextBoolean(),
                                        type.getElementType(),
                                        constContext,
                                        depth,
                                        fuzzer));
        }
      }
      return identityConstructor(
          expr,
          applyIdentityFunction(
              new TypeConstructorExpr(
                  type.toString(),
                  applyIdentityFunction(
                      new TypeConstructorExpr(
                          randomInnerConstructorType.toString(),
                          innerConstructorArgs),
                      randomInnerConstructorType, constContext, depth, fuzzer)),
              type, constContext, depth, fuzzer));
    }
  }

  /**
   * Identity transformation to transpose a matrix twice. When performed, transforms an expression
   * of a matrix m -> transpose(identity(transpose(identity(m)))).
   */
  private class IdentityDoubleTranspose extends AbstractIdentityTransformation {
    private IdentityDoubleTranspose() {
      super(BasicType.allMatrixTypes(), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth,
                      Fuzzer fuzzer) {
      assert type.isMatrix();
      return identityConstructor(
          expr,
          new FunctionCallExpr("transpose",
              applyIdentityFunction(
                  new FunctionCallExpr("transpose",
                      applyIdentityFunction(expr.clone(), type, constContext, depth, fuzzer)),
                  type.transposedMatrixType(), constContext, depth, fuzzer)));
    }

    @Override
    public boolean preconditionHolds(Expr expr, BasicType basicType, boolean constContext) {
      // As discussed in https://github.com/KhronosGroup/glslang/issues/1865, the transpose
      // function is not deemed to be compile-time constant.
      return super.preconditionHolds(expr, basicType, constContext)
          && !constContext;
    }
  }

  /**
   * Identity transformation to multiply a vector or a rectangular matrix P by an identity matrix I,
   * producing the same vector/matrix P as output. This relies on two properties of the identity
   * matrix:
   *     If P is a matrix of size n x m, where n is the number of columns and m the number of rows,
   *     and I is the identity matrix of size n x n, then P * I = P.
   *     If P is a matrix of size n x m, where n is the number of columns and m the number of rows,
   *     and I is the identity matrix of size m x m, then I * P = P.
   *     Note that matrices of size 1 x m or n x 1 are vecm or vecn, respectively.
   * When performed, transforms an expression of a vector or rectangular matrix P ->
   * identity(identityMatrix * P) or identity(P * identityMatrix).
   */
  private class IdentityMatrixMultIdentity extends AbstractIdentityTransformation {
    private IdentityMatrixMultIdentity() {
      super(Stream.concat(BasicType.allNonSquareMatrixTypes().stream(),
          Stream.of(BasicType.VEC2, BasicType.VEC3, BasicType.VEC4))
          .collect(Collectors.toList()), true);
    }

    @Override
    public Expr apply(Expr expr, BasicType type, boolean constContext, int depth, Fuzzer fuzzer) {
      // We use isVector()/isMatrix() in this function for brevity and self-documentation instead of
      // concatenating streams.
      assert type.isVector() || type.isMatrix();
      final boolean usingRightMultiplication = generator.nextBoolean();
      final int identityMatrixSize =
          type.isVector()
          ? type.getNumElements()
          : usingRightMultiplication
              ? type.getNumRows()
              : type.getNumColumns();
      final Expr identityMatrix = makeOpaqueIdentityMatrix(
          BasicType.makeMatrixType(identityMatrixSize, identityMatrixSize),
          constContext, depth, fuzzer);
      // We wrap the original expr in parentheses to prevent issues with ternary operators.
      final Expr binaryMultExpr = usingRightMultiplication
          ? new BinaryExpr(identityMatrix, new ParenExpr(expr.clone()), BinOp.MUL)
          : new BinaryExpr(new ParenExpr(expr.clone()), identityMatrix, BinOp.MUL);
      return identityConstructor(
          expr,
          applyIdentityFunction(binaryMultExpr, type, constContext, depth, fuzzer));
    }
  }

  /**
   * When creating macro invocations, the comma operator is troublesome.  This is because if a
   * comma expression 'a, b' is passed as a macro argument, it is processes as two macro arguments.
   * This helper turns an expression of the form 'a, b' into '(a, b)', and returns expressions of
   * other forms unchanged.
   * @param expr An expression to be potentially enclosed in parentheses.
   * @return The expression unchanged if it is not a comma expression, otherwise the expression in
   *         parentheses.
   */
  private Expr addParenthesesIfCommaExpr(Expr expr) {
    if (expr instanceof BinaryExpr && ((BinaryExpr) expr).getOp() == BinOp.COMMA) {
      return new ParenExpr(expr);
    }
    return expr;
  }

}
