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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.Op;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class TruncateLoops extends StandardVisitor {

  private final int limit;
  private final TranslationUnit tu;
  private final String prefix;
  private final boolean ignoreShortRunningForLoops;
  private int counter;

  public TruncateLoops(int limit, String prefix, TranslationUnit tu,
                       boolean ignoreShortRunningForLoops) {
    this.limit = limit;
    this.tu = tu;
    this.prefix = prefix;
    this.ignoreShortRunningForLoops = ignoreShortRunningForLoops;
    counter = 0;
    visit(tu);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    super.visitForStmt(forStmt);
    if (!ignoreShortRunningForLoops || maybeLongRunning(forStmt)) {
      handleLoop(forStmt);
    }
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    super.visitWhileStmt(whileStmt);
    handleLoop(whileStmt);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    super.visitDoStmt(doStmt);
    handleLoop(doStmt);
  }

  private void handleLoop(LoopStmt loopStmt) {
    final IParentMap parentMap = IParentMap.createParentMap(tu);
    final String limiterName = prefix + "_looplimiter" + counter;
    counter++;

    final DeclarationStmt limiterDeclaration = new DeclarationStmt(
          new VariablesDeclaration(BasicType.INT,
                new VariableDeclInfo(limiterName, null,
                      new ScalarInitializer(new IntConstantExpr("0")))));

    final List<Stmt> limitCheckAndIncrement = Arrays.asList(
          new IfStmt(
            new BinaryExpr(
                  new VariableIdentifierExpr(limiterName),
                  new IntConstantExpr(String.valueOf(limit)),
                  BinOp.GE),
            new BlockStmt(Arrays.asList(new BreakStmt()), true),
            null),
          new ExprStmt(new UnaryExpr(
            new VariableIdentifierExpr(limiterName),
            UnOp.POST_INC)));

    if (loopStmt.getBody() instanceof BlockStmt) {
      for (int i = limitCheckAndIncrement.size() - 1; i >= 0; i--) {
        ((BlockStmt) loopStmt.getBody()).insertStmt(0, limitCheckAndIncrement.get(i));
      }
    } else {
      final List<Stmt> newStmts = new ArrayList<>();
      newStmts.addAll(limitCheckAndIncrement);
      newStmts.add(loopStmt.getBody());
      loopStmt.setBody(new BlockStmt(newStmts, loopStmt instanceof DoStmt));
    }

    final BlockStmt replacementBlock = new BlockStmt(
          Arrays.asList(limiterDeclaration, loopStmt), true);
    parentMap.getParent(loopStmt).replaceChild(loopStmt, replacementBlock);
  }

  private boolean maybeLongRunning(ForStmt forStmt) {
    final Optional<ImmutablePair<String, Integer>> initValueAndLoopCounterName
        = getLoopCounterNameAndInitValue(forStmt.getInit());
    if (!initValueAndLoopCounterName.isPresent()) {
      return false;
    }
    final String loopCounterName = initValueAndLoopCounterName.get().left;
    final int initValue = initValueAndLoopCounterName.get().right;

    final Optional<ImmutablePair<BinOp, Integer>> condTestTypeAndLimitValue
        = getCondTestTypeAndLimitValue(forStmt.getCondition(), loopCounterName);
    if (!condTestTypeAndLimitValue.isPresent()) {
      return false;
    }
    final BinOp condTestType = condTestTypeAndLimitValue.get().left;
    final int condLimitValue = condTestTypeAndLimitValue.get().right;
    if (condTestType.isSideEffecting()) {
      return false;
    }

    final Optional<Integer> incrementValue
        = getIncrementValue(forStmt.getIncrement(), loopCounterName);
    if (!incrementValue.isPresent()) {
      return false;
    }

    return (((condTestType == BinOp.LT || condTestType == BinOp.LE) && incrementValue.get() <= 0)
        || ((condTestType == BinOp.GT || condTestType == BinOp.GE) && incrementValue.get() >= 0)
        || ((condLimitValue - initValue) / incrementValue.get() >= limit));
  }

  private Optional<ImmutablePair<String, Integer>> getLoopCounterNameAndInitValue(Stmt init) {
    String name = null;
    Expr expr = null;
    if (init instanceof ExprStmt
        && ((ExprStmt) init).getExpr() instanceof BinaryExpr
        && ((BinaryExpr)(((ExprStmt) init).getExpr())).getOp() == BinOp.ASSIGN
        && ((BinaryExpr)(((ExprStmt) init).getExpr())).getLhs()
        instanceof VariableIdentifierExpr) {
      name = ((VariableIdentifierExpr) (((BinaryExpr)(((ExprStmt) init)
          .getExpr())).getLhs())).getName();
      expr = ((BinaryExpr)(((ExprStmt) init)
          .getExpr())).getRhs();
    } else if (init instanceof DeclarationStmt
        && ((DeclarationStmt) init).getVariablesDeclaration().getNumDecls() == 1
        && ((DeclarationStmt) init).getVariablesDeclaration().getDeclInfo(0)
        .getInitializer() instanceof ScalarInitializer) {
      name = ((DeclarationStmt) init).getVariablesDeclaration()
          .getDeclInfo(0).getName();
      expr = ((ScalarInitializer)((DeclarationStmt) init)
          .getVariablesDeclaration().getDeclInfo(0)
          .getInitializer()).getExpr();
    }
    if (name == null || expr == null) {
      return Optional.empty();
    }
    Optional<Integer> constant = getAsConstant(expr);
    if (!constant.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(new ImmutablePair<>(name, constant.get()));
  }

  private Optional<ImmutablePair<BinOp,Integer>> getCondTestTypeAndLimitValue(Expr cond,
      String loopCounterName) {
    if (!(cond instanceof BinaryExpr)) {
      return Optional.empty();
    }
    final BinaryExpr binaryExprCond = (BinaryExpr) cond;
    Optional<Integer> condLimitValue = getAsConstant(binaryExprCond.getRhs());
    if (condLimitValue.isPresent()
        && isSameVarIdentifier(binaryExprCond.getLhs(), loopCounterName)) {
      return Optional.of(new ImmutablePair<>(binaryExprCond.getOp(), condLimitValue.get()));
    }
    condLimitValue = getAsConstant(binaryExprCond.getLhs());
    if (condLimitValue.isPresent()
        && isSameVarIdentifier(binaryExprCond.getRhs(), loopCounterName)) {
      return Optional.of(new ImmutablePair<>(switchCondTestType(binaryExprCond.getOp()),
          condLimitValue.get()));
    }

    return Optional.empty();
  }

  private Optional<Integer> getIncrementValue(Expr incr, String loopCounterName) {
    if (incr instanceof UnaryExpr
        && isSameVarIdentifier(((UnaryExpr) incr).getExpr(), loopCounterName)) {
      switch (((UnaryExpr) incr).getOp()) {
        case POST_INC:
        case PRE_INC:
          return Optional.of(1);
        case POST_DEC:
        case PRE_DEC:
          return Optional.of(-1);
        default:
          return Optional.empty();
      }
    }
    if (incr instanceof BinaryExpr
        && isSameVarIdentifier(((BinaryExpr) incr).getLhs(), loopCounterName)) {
      final Optional<Integer> incrementValue = getAsConstant(((BinaryExpr) incr).getRhs());
      if (!incrementValue.isPresent()) {
        return Optional.empty();
      }
      switch (((BinaryExpr) incr).getOp()) {
        case ADD_ASSIGN:
          return incrementValue;
        case SUB_ASSIGN:
          return incrementValue.map(item -> -item);
        default:
          return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private boolean isSameVarIdentifier(Expr expr, String loopCounterName) {
    return expr instanceof VariableIdentifierExpr
        && ((VariableIdentifierExpr)expr).getName().equals(loopCounterName);
  }

  private Optional<Integer> getAsConstant(Expr expr) {
    if (expr instanceof ParenExpr) {
      return getAsConstant(((ParenExpr)expr).getExpr());
    }
    if (expr instanceof IntConstantExpr) {
      return Optional.of(Integer.valueOf(((IntConstantExpr) expr).getValue()));
    }
    if (expr instanceof UnaryExpr) {
      final UnaryExpr unaryExpr = (UnaryExpr) expr;
      switch (unaryExpr.getOp()) {
        case MINUS:
          return getAsConstant(unaryExpr.getExpr()).map(item -> -item);
        case PLUS:
          return getAsConstant(unaryExpr.getExpr());
        default:
          return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private BinOp switchCondTestType(BinOp binOp) {
    switch (binOp) {
      case LT:
        return BinOp.GT;
      case GT:
        return BinOp.LT;
      case LE:
        return BinOp.GE;
      case GE:
        return BinOp.LE;
      default:
        return binOp;
    }
  }

}
