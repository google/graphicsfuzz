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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.ConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.SideEffectChecker;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public final class FoldConstantReductionOpportunities extends SimplifyExprReductionOpportunities {

  private FoldConstantReductionOpportunities(TranslationUnit tu,
                                           ReducerContext context) {
    super(tu, context);
  }

  @Override
  void identifyReductionOpportunitiesForChild(IAstNode parent, Expr child) {

    Optional<FunctionCallExpr> maybeFce = asFunctionCallExpr(child);
    if (maybeFce.isPresent()) {
      switch (maybeFce.get().getCallee()) {
        case "sin":
          assert maybeFce.get().getNumArgs() == 1;
          if (isZeroFloat(maybeFce.get().getArg(0))) {
            addReplaceWithZero(parent, child);
          }
          return;
        case "cos":
          assert maybeFce.get().getNumArgs() == 1;
          if (isZeroFloat(maybeFce.get().getArg(0))) {
            addReplaceWithOne(parent, child);
          }
          return;
        default:
          return;
      }
    }

    Optional<BinaryExpr> maybeBe = asBinaryExpr(child);
    if (maybeBe.isPresent()) {
      final Expr lhs = maybeBe.get().getLhs();
      final Expr rhs = maybeBe.get().getRhs();
      switch (maybeBe.get().getOp()) {

        // TODO: Gradually make this more capable towards full-on constant folding.
        // An issue is: at what precision should we do the constant folding?
        // For starters, let's use Java's precision and see how this goes in practice.

        case ADD:
          findFoldAddZeroOpportunities(parent, child, lhs, rhs);
          findFoldAddZeroOpportunities(parent, child, rhs, lhs);
          findFoldFpAddOpportunities(parent, child, lhs, rhs);
          findFoldIntAddOpportunities(parent, child, lhs, rhs);
          findFoldUintAddOpportunities(parent, child, lhs, rhs);
          findFoldFpScalarVectorAddOpportunities(parent, child, lhs, rhs);
          return;

        case SUB:
          findFoldSomethingSubZeroOpportunities(parent, child, lhs, rhs);
          findFoldZeroSubSomethingOpportunities(parent, child, lhs, rhs);
          findFoldFpSubOpportunities(parent, child, lhs, rhs);
          findFoldFpScalarVectorSubOpportunities(parent, child, lhs, rhs);
          return;

        case MUL:
          findFoldMulIdentityOpportunities(parent, child, lhs, rhs);
          findFoldMulIdentityOpportunities(parent, child, rhs, lhs);
          findFoldMulZeroOpportunities(parent, child, lhs, rhs);
          findFoldMulZeroOpportunities(parent, child, rhs, lhs);
          findFoldFpMulOpportunities(parent, child, lhs, rhs);
          findFoldFpScalarVectorMulOpportunities(parent, child, lhs, rhs);
          return;

        case DIV:
          findFoldSomethingDivOneOpportunities(parent, child, lhs, rhs);
          return;

        default:
          return;
      }
    }

    Optional<UnaryExpr> maybeUe = asUnaryExpr(child);
    if (maybeUe.isPresent()) {
      final Expr arg = maybeUe.get().getExpr();
      switch (maybeUe.get().getOp()) {
        case PLUS:
          findFoldPlusMinusZeroOpportunities(parent, child, arg);
          return;
        case MINUS:
          findFoldPlusMinusZeroOpportunities(parent, child, arg);
          return;
        default:
          return;
      }
    }

    Optional<TypeConstructorExpr> maybeTypeConstructorExpr = asTypeConstructorExpr(child);
    if (maybeTypeConstructorExpr.isPresent()) {
      final String typename = maybeTypeConstructorExpr.get().getTypename();
      if (typename.equals(BasicType.INT.toString())) {
        Optional<Integer> maybeInteger =
            tryGetFloatConstantAsInteger(maybeTypeConstructorExpr.get().getArg(0));
        if (maybeInteger.isPresent()) {
          addReplaceWithExpr(parent, child, new IntConstantExpr(
              String.valueOf(maybeInteger.get())));
        }
      }
      if (typename.equals(BasicType.UINT.toString())) {
        Optional<Integer> maybeInteger =
            tryGetFloatConstantAsInteger(maybeTypeConstructorExpr.get().getArg(0));
        if (maybeInteger.isPresent()) {
          addReplaceWithExpr(parent, child, new UIntConstantExpr(maybeInteger.get() + "u"));
        }
      }
      return;
    }

    Optional<ParenExpr> maybeParen = asParenExpr(child);
    if (maybeParen.isPresent()) {
      findRemoveParenOpportunities(parent, child, maybeParen.get().getExpr());
    }

    Optional<MemberLookupExpr> maybeMemberLookup = asMemberLookupExpr(child);
    if (maybeMemberLookup.isPresent()) {
      findReplaceTypeConstructorWithElementOpportunities(parent, child,
          maybeMemberLookup.get());
    }

  }

  private Optional<Integer> tryGetFloatConstantAsInteger(Expr expr) {
    if (!(expr instanceof FloatConstantExpr)) {
      return Optional.empty();
    }
    // Check that the string representation has the form "digits.digits" or "digits."
    final FloatConstantExpr floatConstantExpr = (FloatConstantExpr) expr;
    if (!floatConstantExpr.getValue().contains(".")) {
      return Optional.empty();
    }
    for (int i = 0; i < floatConstantExpr.getValue().length(); i++) {
      if (floatConstantExpr.getValue().charAt(i) == '.') {
        continue;
      }
      if (!Character.isDigit(floatConstantExpr.getValue().charAt(i))) {
        return Optional.empty();
      }
    }

    if (floatConstantExpr.getValue().startsWith(".")) {
      // No digits before the decimal point, so this number is zero if and only if the digits after
      // the point are zero.
      if (Integer.parseInt(floatConstantExpr.getValue().substring(1)) != 0) {
        return Optional.empty();
      }
      return Optional.of(0);
    } else if (floatConstantExpr.getValue().endsWith(".")) {
      // No digits after the point, so this number is integer-valued.
      return Optional.of(Integer.parseInt(floatConstantExpr.getValue().substring(0,
          floatConstantExpr.getValue().length() - 1)));
    }
    // There are digits both sides of the decimal point.
    final String[] components = floatConstantExpr.getValue().split("\\.");
    assert components.length == 2;
    // Check whether the digits after the point parse to 0.
    if (Integer.parseInt(components[1]) != 0) {
      return Optional.empty();
    }
    // We have 0 after the decimal point, so we can interpret the digits before as an integer.
    return Optional.of(Integer.parseInt(components[0]));
  }

  private void findReplaceTypeConstructorWithElementOpportunities(
      IAstNode parent,
      Expr child,
      MemberLookupExpr memberLookupExpr) {
    if (!(memberLookupExpr.getStructure() instanceof TypeConstructorExpr)) {
      return;
    }
    final TypeConstructorExpr tce = (TypeConstructorExpr) memberLookupExpr.getStructure();
    if (!Arrays.asList("x", "y", "z", "w", "r", "g", "b", "a", "s", "t", "p", "q")
        .contains(memberLookupExpr.getMember())) {
      return; // We could handle swizzles, but for now we do not.
    }
    final Type structureType = typer.lookupType(memberLookupExpr.getStructure());
    if (structureType == null || !(structureType instanceof BasicType)) {
      return;
    }
    final BasicType basicType = (BasicType) structureType;
    if (!BasicType.allVectorTypes().contains(basicType)) {
      return;
    }
    if (basicType.getNumElements() != tce.getNumArgs()) {
      // We could handle cases such as vec2(0.0).x resolving to 0.0; but for now we do not.
      return;
    }
    if (!SideEffectChecker.isSideEffectFree(tce, context.getShadingLanguageVersion())) {
      // We mustn't eliminate side-effects from elements of the vector that we are not popping out.
      return;
    }
    int index;
    switch (memberLookupExpr.getMember()) {
      case "x":
      case "r":
      case "s":
        index = 0;
        break;
      case "y":
      case "g":
      case "t":
        index = 1;
        break;
      case "z":
      case "b":
      case "p":
        index = 2;
        break;
      case "w":
      case "a":
      case "q":
        index = 3;
        break;
      default:
        throw new RuntimeException("Should be unreachable.");
    }
    addReplaceWithExpr(parent, child, new ParenExpr(tce.getArg(index)));
  }

  private void findRemoveParenOpportunities(IAstNode parent, Expr child, Expr expr) {
    if (expr instanceof ConstantExpr
        || expr instanceof VariableIdentifierExpr
        || expr instanceof ParenExpr
        || expr instanceof FunctionCallExpr
        || expr instanceof MemberLookupExpr
        || expr instanceof TypeConstructorExpr) {
      addReplaceWithExpr(parent, child, expr);
    }
  }

  private void findFoldPlusMinusZeroOpportunities(IAstNode parent, Expr child, Expr arg) {
    if (isZeroFloat(arg)) {
      addReplaceWithExpr(parent, child, makeZeroFloat());
    }
    if (isZeroInt(arg)) {
      addReplaceWithExpr(parent, child, makeZeroInt());
    }
  }

  private void findFoldAddZeroOpportunities(IAstNode parent,
                                            Expr child,
                                            Expr thisHandSide,
                                            Expr thatHandSide) {
    if (isZeroFloat(thisHandSide)) {
      addReplaceWithExpr(parent, child, thatHandSide);
    }
    final Type childType = typer.lookupType(child);
    final Type thatHandSideType = typer.lookupType(thatHandSide);
    if (childType != null && thatHandSideType != null
        && childType.getWithoutQualifiers().equals(thatHandSideType.getWithoutQualifiers())) {
      if (isZeroFloatVecOrSquareMat(thisHandSide)) {
        addReplaceWithExpr(parent, child, thatHandSide);
      }
    }
  }

  private void findFoldMulIdentityOpportunities(IAstNode parent,
                                            Expr child,
                                            Expr thisHandSide,
                                            Expr thatHandSide) {
    if (isOneFloat(thisHandSide)) {
      addReplaceWithExpr(parent, child, thatHandSide);
    }
    final Type childType = typer.lookupType(child);
    final Type thisHandSideType = typer.lookupType(thisHandSide);
    if (childType != null && thisHandSideType != null
        && childType.getWithoutQualifiers().equals(thisHandSideType.getWithoutQualifiers())) {
      if (isOneFloatVec(thisHandSide) || isIdentityMatrix(thisHandSide)) {
        addReplaceWithExpr(parent, child, thatHandSide);
      }
    }
  }

  private void findFoldMulZeroOpportunities(IAstNode parent,
                                                Expr child,
                                                Expr thisHandSide,
                                                Expr thatHandSide) {
    if (isZeroFloat(thisHandSide)) {
      addReplaceWithZero(parent, child);
    }
    final Type childType = typer.lookupType(child);
    final Type thatHandSideType = typer.lookupType(thatHandSide);
    if (childType != null && thatHandSideType != null
        && childType.getWithoutQualifiers().equals(thatHandSideType.getWithoutQualifiers())) {
      if (isZeroFloatVecOrSquareMat(thisHandSide)) {
        addReplaceWithZero(parent, child);
      }
    }
  }

  private void findFoldSomethingSubZeroOpportunities(IAstNode parent,
                                            Expr child,
                                            Expr lhs,
                                            Expr rhs) {
    if (isZeroFloat(rhs)) {
      addReplaceWithExpr(parent, child, lhs);
    }
    final Type childType = typer.lookupType(child);
    final Type lhsType = typer.lookupType(lhs);
    if (childType != null && lhsType != null
        && childType.getWithoutQualifiers().equals(lhsType.getWithoutQualifiers())) {
      if (isZeroFloatVecOrSquareMat(rhs)) {
        addReplaceWithExpr(parent, child, lhs);
      }
    }
  }

  private void findFoldZeroSubSomethingOpportunities(IAstNode parent,
                                                     Expr child,
                                                     Expr lhs,
                                                     Expr rhs) {
    if (isZeroFloat(lhs)) {
      addReplaceWithExpr(parent, child, new ParenExpr(new UnaryExpr(rhs, UnOp.MINUS)));
    }
    final Type childType = typer.lookupType(child);
    final Type rhsType = typer.lookupType(rhs);
    if (childType != null && rhsType != null
        && childType.getWithoutQualifiers().equals(rhsType.getWithoutQualifiers())) {
      if (isZeroFloatVecOrSquareMat(lhs)) {
        addReplaceWithExpr(parent, child, new ParenExpr(new UnaryExpr(rhs, UnOp.MINUS)));
      }
    }
  }

  private void findFoldSomethingDivOneOpportunities(IAstNode parent,
                                                     Expr child,
                                                     Expr lhs,
                                                     Expr rhs) {
    if (isOneFloat(rhs)) {
      addReplaceWithExpr(parent, child, lhs);
    }
    final Type childType = typer.lookupType(child);
    final Type lhsType = typer.lookupType(lhs);
    if (childType != null && lhsType != null
        && childType.getWithoutQualifiers().equals(lhsType.getWithoutQualifiers())) {
      if (isOneFloatVec(rhs)) {
        addReplaceWithExpr(parent, child, lhs);
      }
    }
  }

  private void findFoldFpAddOpportunities(IAstNode parent,
                                          Expr child,
                                          Expr lhs,
                                          Expr rhs) {
    findFoldFpBinaryOpportunities(parent, child, lhs, rhs, Float::sum);
  }

  private void findFoldIntAddOpportunities(IAstNode parent,
                                          Expr child,
                                          Expr lhs,
                                          Expr rhs) {
    findFoldIntBinaryOpportunities(parent, child, lhs, rhs, Integer::sum);
  }

  private void findFoldUintAddOpportunities(IAstNode parent,
                                       Expr child,
                                       Expr lhs,
                                       Expr rhs) {
    findFoldUintBinaryOpportunities(parent, child, lhs, rhs, Integer::sum);
  }

  private void findFoldFpSubOpportunities(IAstNode parent,
                                          Expr child,
                                          Expr lhs,
                                          Expr rhs) {
    findFoldFpBinaryOpportunities(parent, child, lhs, rhs, (first, second) -> first - second);
  }

  private void findFoldFpMulOpportunities(IAstNode parent,
                                          Expr child,
                                          Expr lhs,
                                          Expr rhs) {
    findFoldFpBinaryOpportunities(parent, child, lhs, rhs, (first, second) -> first * second);
  }

  private void findFoldFpBinaryOpportunities(IAstNode parent, Expr child, Expr lhs, Expr rhs,
                                             BinaryOperator<Float> op) {
    if (isFpConstant(lhs) && isFpConstant(rhs)) {
      addReplaceWithExpr(parent, child, new FloatConstantExpr(
          op.apply(Float.valueOf(((FloatConstantExpr) lhs).getValue()),
              Float.valueOf(((FloatConstantExpr) rhs).getValue())).toString()));
    }
  }

  private void findFoldIntBinaryOpportunities(IAstNode parent, Expr child, Expr lhs, Expr rhs,
                                             BinaryOperator<Integer> op) {
    if (isIntConstant(lhs) && isIntConstant(rhs)) {
      addReplaceWithExpr(parent, child, new IntConstantExpr(
             op.apply(
            ((IntConstantExpr) lhs).getNumericValue(),
            ((IntConstantExpr) rhs).getNumericValue()).toString()));
    }
  }

  private void findFoldUintBinaryOpportunities(IAstNode parent, Expr child, Expr lhs, Expr rhs,
                                              BinaryOperator<Integer> op) {
    if (isUintConstant(lhs) && isUintConstant(rhs)) {
      addReplaceWithExpr(parent, child, new UIntConstantExpr(
          op.apply(
              ((UIntConstantExpr) lhs).getNumericValue(),
              ((UIntConstantExpr) rhs).getNumericValue()).toString() + "u"));
    }
  }

  private void findFoldFpScalarVectorAddOpportunities(IAstNode parent,
                                                      Expr child,
                                                      Expr lhs,
                                                      Expr rhs) {
    findFoldFpScalarVectorBinaryOpportunities(parent, child, lhs, rhs, Float::sum);
  }

  private void findFoldFpScalarVectorSubOpportunities(IAstNode parent,
                                                      Expr child,
                                                      Expr lhs,
                                                      Expr rhs) {
    findFoldFpScalarVectorBinaryOpportunities(parent, child, lhs, rhs,
        (first, second) -> first - second);
  }

  private void findFoldFpScalarVectorMulOpportunities(IAstNode parent,
                                                      Expr child,
                                                      Expr lhs,
                                                      Expr rhs) {
    findFoldFpScalarVectorBinaryOpportunities(parent, child, lhs, rhs,
        (first, second) -> first * second);
  }

  private void findFoldFpScalarVectorBinaryOpportunities(IAstNode parent,
                                                         Expr child,
                                                         Expr lhs,
                                                         Expr rhs,
                                                         BinaryOperator<Float> op) {
    if (isFpConstant(lhs) && isFpVectorConstant(rhs)) {
      final TypeConstructorExpr typeConstructorExpr = (TypeConstructorExpr) rhs;
      addReplaceWithExpr(parent, child,
          new TypeConstructorExpr(typeConstructorExpr.getTypename(),
              typeConstructorExpr.getArgs()
                  .stream()
                  .map(item -> (FloatConstantExpr) item)
                  .map(item -> new FloatConstantExpr(op.apply(
                      Float.valueOf(((FloatConstantExpr) lhs).getValue()),
                      Float.valueOf(item.getValue())).toString()))
                  .collect(Collectors.toList())));
    }

    if (isFpConstant(rhs) && isFpVectorConstant(lhs)) {
      final TypeConstructorExpr typeConstructorExpr = (TypeConstructorExpr) lhs;
      addReplaceWithExpr(parent, child,
          new TypeConstructorExpr(typeConstructorExpr.getTypename(),
              typeConstructorExpr.getArgs()
                  .stream()
                  .map(item -> (FloatConstantExpr) item)
                  .map(item -> new FloatConstantExpr(op.apply(
                      Float.valueOf(item.getValue()),
                      Float.valueOf(((FloatConstantExpr) rhs).getValue())).toString()))
                  .collect(Collectors.toList())));
    }
  }


  static List<SimplifyExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReducerContext context) {
    FoldConstantReductionOpportunities finder = new FoldConstantReductionOpportunities(tu,
        context);
    finder.visit(tu);
    return finder.getOpportunities();
  }

  private Optional<FunctionCallExpr> asFunctionCallExpr(Expr expr) {
    return expr instanceof FunctionCallExpr
        ? Optional.of((FunctionCallExpr) expr)
        : Optional.empty();
  }

  private Optional<BinaryExpr> asBinaryExpr(Expr expr) {
    return expr instanceof BinaryExpr
        ? Optional.of((BinaryExpr) expr)
        : Optional.empty();
  }

  private Optional<UnaryExpr> asUnaryExpr(Expr expr) {
    return expr instanceof UnaryExpr
        ? Optional.of((UnaryExpr) expr)
        : Optional.empty();
  }

  private Optional<ParenExpr> asParenExpr(Expr expr) {
    return expr instanceof ParenExpr
        ? Optional.of((ParenExpr) expr)
        : Optional.empty();
  }

  private Optional<MemberLookupExpr> asMemberLookupExpr(Expr expr) {
    return expr instanceof MemberLookupExpr
        ? Optional.of((MemberLookupExpr) expr)
        : Optional.empty();
  }

  private Optional<TypeConstructorExpr> asTypeConstructorExpr(Expr expr) {
    return expr instanceof TypeConstructorExpr
        ? Optional.of((TypeConstructorExpr) expr)
        : Optional.empty();
  }

  private boolean isZeroFloat(Expr expr) {
    return isFloatValue(expr, Arrays.asList("0.0", "0."));
  }

  private boolean isOneFloat(Expr expr) {
    return isFloatValue(expr, Arrays.asList("1.0", "1."));
  }

  private boolean isFloatValue(Expr expr, List<String> values) {
    if (!(expr instanceof FloatConstantExpr)) {
      return false;
    }
    return values.contains(((FloatConstantExpr) expr).getValue());
  }

  private boolean isZeroFloatVecOrSquareMat(Expr expr) {
    if (!(expr instanceof TypeConstructorExpr)) {
      return false;
    }
    if (!Arrays.asList(BasicType.VEC2, BasicType.VEC3, BasicType.VEC4,
        BasicType.MAT2X2, BasicType.MAT3X3, BasicType.MAT4X4).contains(typer.lookupType(expr))) {
      return false;
    }
    return ((TypeConstructorExpr) expr).getArgs()
        .stream().allMatch(item -> isZeroFloat(item) || isZeroFloatVecOrSquareMat(item));
  }

  private boolean isOneFloatVec(Expr expr) {
    if (!(expr instanceof TypeConstructorExpr)) {
      return false;
    }
    if (!Arrays.asList(BasicType.VEC2, BasicType.VEC3, BasicType.VEC4)
        .contains(typer.lookupType(expr))) {
      return false;
    }
    return ((TypeConstructorExpr) expr).getArgs()
        .stream().allMatch(item -> isOneFloat(item) || isOneFloatVec(item));
  }

  private boolean isIdentityMatrix(Expr expr) {
    if (!(expr instanceof TypeConstructorExpr)) {
      return false;
    }
    final TypeConstructorExpr tce = (TypeConstructorExpr) expr;
    final Type exprType = typer.lookupType(expr);
    if (!Arrays.asList(BasicType.MAT2X2, BasicType.MAT3X3, BasicType.MAT4X4)
        .contains(exprType)) {
      return false;
    }
    if (tce.getNumArgs() == 1 && isOneFloat(tce.getArg(0))) {
      return true;
    }

    // Check whether the matrix constructor has the exact form of an identity matrix.
    final int dim = (int) Math.sqrt(((BasicType) exprType).getNumElements());
    if (tce.getNumArgs() != dim * dim) {
      return false;
    }
    for (int i = 0; i < dim; i++) {
      for (int j = 0; j < dim; j++) {
        final Expr element = tce.getArg(i * dim + j);
        if (i == j) {
          if (!isOneFloat(element)) {
            return false;
          }
        } else if (!isZeroFloat(element)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isZeroInt(Expr expr) {
    return isIntValue(expr, Arrays.asList("0"));
  }

  private static boolean isIntValue(Expr expr, List<String> values) {
    if (!(expr instanceof IntConstantExpr)) {
      return false;
    }
    return values.contains(((IntConstantExpr) expr).getValue());
  }

  private static boolean isFpConstant(Expr expr) {
    return expr instanceof FloatConstantExpr;
  }

  private static boolean isIntConstant(Expr expr) {
    return expr instanceof IntConstantExpr;
  }

  private static boolean isUintConstant(Expr expr) {
    return expr instanceof UIntConstantExpr;
  }

  private static boolean isFpVectorConstant(Expr expr) {
    return expr instanceof TypeConstructorExpr
        && Arrays.asList(BasicType.VEC2.toString(),
                         BasicType.VEC3.toString(),
                         BasicType.VEC4.toString())
                           .contains(((TypeConstructorExpr) expr).getTypename())
        && ((TypeConstructorExpr) expr).getArgs()
                           .stream()
                           .allMatch(FoldConstantReductionOpportunities::isFpConstant);
  }

  private Expr makeZeroFloat() {
    return new FloatConstantExpr("0.0");
  }

  private Expr makeOneFloat() {
    return new FloatConstantExpr("1.0");
  }

  private Expr makeZeroInt() {
    return new IntConstantExpr("0");
  }

  private void addReplaceWithZero(IAstNode parent, Expr child) {
    addReplaceWithExpr(parent, child, makeZeroFloat());
  }

  private void addReplaceWithOne(IAstNode parent, Expr child) {
    addReplaceWithExpr(parent, child, makeOneFloat());
  }

  private void addReplaceWithExpr(IAstNode parent, Expr child, Expr newChild) {
    addOpportunity(new SimplifyExprReductionOpportunity(
        parent,
        newChild,
        child,
        getVistitationDepth()));
  }

}
