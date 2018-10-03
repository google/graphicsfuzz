package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class FoldConstantReductionOpportunities extends SimplifyExprReductionOpportunities {

  private FoldConstantReductionOpportunities(TranslationUnit tu,
                                           ReductionOpportunityContext context) {
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

        // TODO: consider doing full-on constant folding here.
        // An issue is: at what precision should we do the constant folding?
        // While we figure this out, let's just do the identity and annihilator cases.

        case ADD:

          findFoldAddZeroOpportunities(parent, child, lhs, rhs);
          findFoldAddZeroOpportunities(parent, child, rhs, lhs);
          return;

        case SUB:
          findFoldSomethingSubZeroOpportunities(parent, child, lhs, rhs);
          findFoldZeroSubSomethingOpportunities(parent, child, lhs, rhs);
          return;

        case MUL:
          findFoldMulIdentityOpportunities(parent, child, lhs, rhs);
          findFoldMulIdentityOpportunities(parent, child, rhs, lhs);
          findFoldMulZeroOpportunities(parent, child, lhs, rhs);
          findFoldMulZeroOpportunities(parent, child, rhs, lhs);
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

  static List<SimplifyExprReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReductionOpportunityContext context) {
    return shaderJob.getShaders()
        .stream()
        .map(item -> findOpportunitiesForShader(item, context))
        .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private static List<SimplifyExprReductionOpportunity> findOpportunitiesForShader(
      TranslationUnit tu,
      ReductionOpportunityContext context) {
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

  private boolean isZeroInt(Expr expr) {
    return isIntValue(expr, Arrays.asList("0"));
  }

  private boolean isIntValue(Expr expr, List<String> values) {
    if (!(expr instanceof IntConstantExpr)) {
      return false;
    }
    return values.contains(((IntConstantExpr) expr).getValue());
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
