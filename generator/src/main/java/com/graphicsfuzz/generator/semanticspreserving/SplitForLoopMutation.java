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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.transformreduce.ReplaceLoopCounter;
import com.graphicsfuzz.common.util.ContainsTopLevelBreak;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.util.Constants;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SplitForLoopMutation implements Mutation {

  private final IInjectionPoint injectionPoint;
  private final IRandom random;

  public SplitForLoopMutation(IInjectionPoint injectionPoint, IRandom random) {
    this.injectionPoint = injectionPoint;
    this.random = random;
  }

  @Override
  public void apply() {
    assert suitableForSplitting(injectionPoint);
    injectionPoint.replaceNext(splitForLoop());
  }

  private Stmt splitForLoop() {
    assert injectionPoint.getNextStmt() instanceof ForStmt;

    ForStmt original = (ForStmt) injectionPoint.getNextStmt();
    LoopSplitInfo loopSplitInfo = maybeGetLoopSplitInfo(original).get();

    String newLoopCounter = Constants.SPLIT_LOOP_COUNTER_PREFIX
        + loopSplitInfo.getLoopCounter();

    ForStmt firstLoop = cloneWithReplacedLoopCounter(original,
        loopSplitInfo.getLoopCounter(), newLoopCounter);

    ForStmt secondLoop = cloneWithReplacedLoopCounter(original,
        loopSplitInfo.getLoopCounter(), newLoopCounter);

    int numIterationsToSplitAfter = random.nextInt(
        Math.abs(loopSplitInfo.getStartValue() - loopSplitInfo.getEndValue()) + 1);

    adjustBound(firstLoop, numIterationsToSplitAfter, loopSplitInfo, newLoopCounter);

    adjustInitializer(secondLoop, numIterationsToSplitAfter, loopSplitInfo);

    return new BlockStmt(
        Arrays.asList(firstLoop, secondLoop), true);
  }

  private void adjustBound(ForStmt loop, int numIterationsToSplitAfter,
                           LoopSplitInfo loopSplitInfo,
                           String newLoopCounter) {
    final int newBound = loopSplitInfo.getStartValue()
        + (loopSplitInfo.getIncreasing() ? 1 : -1) * numIterationsToSplitAfter;
    final BinOp newOp = loopSplitInfo.getIncreasing() ? BinOp.LT : BinOp.GT;
    loop.setCondition(
        new BinaryExpr(
            new VariableIdentifierExpr(newLoopCounter),
            new IntConstantExpr(String.valueOf(newBound)),
            newOp));
  }

  private void adjustInitializer(ForStmt loop, int numIterationsToSplitAfter,
                                 LoopSplitInfo loopSplitInfo) {
    final int newStart = loopSplitInfo.getStartValue()
        + (loopSplitInfo.getIncreasing() ? 1 : -1) * numIterationsToSplitAfter;

    VariablesDeclaration varDecl = ((DeclarationStmt) loop.getInit()).getVariablesDeclaration();
    varDecl.getDeclInfo(0).setInitializer(new ScalarInitializer(new IntConstantExpr(
        String.valueOf(newStart))));
  }


  private static ForStmt cloneWithReplacedLoopCounter(ForStmt original,
                                                      String oldLoopCounter,
                                                      String newLoopCounter) {
    return ReplaceLoopCounter.replaceLoopCounter(original.clone(), oldLoopCounter, newLoopCounter);
  }

  private static Optional<LoopSplitInfo> maybeGetLoopSplitInfo(ForStmt forStmt) {

    // We cannot (easily) split a loop that contains a top-level break statement, as we'd have
    // to record that a break from the first loop should prevent execution of the second loop
    if (ContainsTopLevelBreak.check(forStmt.getBody())) {
      return Optional.empty();
    }

    // We need the loop increment to be ++ or --.  First, check that it is a unary expression
    Expr increment = forStmt.getIncrement();
    if (!(increment instanceof UnaryExpr)) {
      // Note: instanceof handles the case where there is no increment
      return Optional.empty();
    }

    // Next, work out whether it is ++ or --; pre vs. post increment does not matter
    boolean loopCounterIncreasing;
    switch (((UnaryExpr) increment).getOp()) {
      case PRE_INC:
      case POST_INC:
        loopCounterIncreasing = true;
        break;
      case PRE_DEC:
      case POST_DEC:
        loopCounterIncreasing = false;
        break;
      default:
        return Optional.empty();
    }

    // Now we need to check whether we're incrementing a variable, and grab its name if so.
    Expr incrementTarget = ((UnaryExpr) increment).getExpr();
    if (!maybeGetName(incrementTarget).isPresent()) {
      return Optional.empty();
    }
    final String loopCounterName = maybeGetName(incrementTarget).get();

    // Next we check that the loop initialiser declares this, and only this, variable.
    if (!(forStmt.getInit() instanceof DeclarationStmt)) {
      return Optional.empty();
    }
    DeclarationStmt initDeclaration = (DeclarationStmt) forStmt.getInit();
    if (initDeclaration.getVariablesDeclaration().getNumDecls() != 1) {
      return Optional.empty();
    }

    // We check that the declared variable is an integer.
    if (!(initDeclaration.getVariablesDeclaration().getBaseType().getWithoutQualifiers()
        == BasicType.INT
        || initDeclaration.getVariablesDeclaration().getBaseType().getWithoutQualifiers()
        == BasicType.UINT)) {
      return Optional.empty();
    }
    VariableDeclInfo declInfo = initDeclaration.getVariablesDeclaration().getDeclInfo(0);

    // It cannot be an array.
    if (declInfo.hasArrayInfo()) {
      return Optional.empty();
    }

    // It must have the expected name.
    if (!declInfo.getName().equals(loopCounterName)) {
      return Optional.empty();
    }

    // Now we grab the initial value, which needs to be an integer.
    if (!(declInfo.getInitializer() instanceof ScalarInitializer)) {
      return Optional.empty();
    }

    // Now we get its integer value, if it has one
    final Optional<Integer> maybeStartValue = maybeGetIntegerValue(((ScalarInitializer) declInfo
        .getInitializer()).getExpr());
    if (!maybeStartValue.isPresent()) {
      return Optional.empty();
    }
    final Integer startValue = maybeStartValue.get();

    // At this point, we have a name, an initial value, and a direction.  We move on to analyse
    // the condition.

    // It needs to be one of <, <=, >, >= or !=.
    if (!(forStmt.getCondition() instanceof BinaryExpr)) {
      return Optional.empty();
    }
    BinaryExpr test = (BinaryExpr) forStmt.getCondition();
    BinOp comparison = ((BinaryExpr) forStmt.getCondition()).getOp();
    if (!Arrays.asList(BinOp.LT, BinOp.LE, BinOp.GT, BinOp.GE, BinOp.NE).contains(comparison)) {
      return Optional.empty();
    }

    // We now work out the end value, and whether the test has the form
    // VARIABLE OP LITERAL or LITERAL OP VARIABLE
    Integer endValue;
    boolean variableBeforeLiteral;
    Optional<String> lhsAsString = maybeGetName(test.getLhs());
    if (lhsAsString.isPresent()) {
      if (!lhsAsString.get().equals(loopCounterName)) {
        return Optional.empty();
      }
      Optional<Integer> rhsAsInt = maybeGetIntegerValue(test.getRhs());
      if (rhsAsInt.isPresent()) {
        endValue = rhsAsInt.get();
        variableBeforeLiteral = true;
      } else {
        return Optional.empty();
      }
    } else {
      Optional<String> rhsAsString = maybeGetName(test.getRhs());
      if (rhsAsString.isPresent()) {
        if (!rhsAsString.get().equals(loopCounterName)) {
          return Optional.empty();
        }
        Optional<Integer> lhsAsInt = maybeGetIntegerValue(test.getLhs());
        if (lhsAsInt.isPresent()) {
          endValue = lhsAsInt.get();
          variableBeforeLiteral = false;
        } else {
          return Optional.empty();
        }
      } else {
        return Optional.empty();
      }
    }

    // Now do some sanity checking: if we're going up, endValue should be larger than
    // startValue, reverse should be true otherwise.
    // If we're going up, test should be <, <= or !=.
    // If we're going down, test should be >, >= or !=.
    if (!areDirectionBoundsAndTestConsistent(loopCounterIncreasing, startValue, endValue,
        comparison, variableBeforeLiteral)) {
      return Optional.empty();
    }

    // Finally, check that the loop counter is not modified in the loop body.
    if (bodyModifiesLoopCounter(forStmt, loopCounterName)) {
      return Optional.empty();
    }

    return Optional.of(new LoopSplitInfo(loopCounterName, startValue, endValue,
        loopCounterIncreasing));

  }

  private static boolean bodyModifiesLoopCounter(final ForStmt forStmt,
                                                 final String loopCounterName) {
    return new StandardVisitor() {

      private boolean foundModification = false;

      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (binaryExpr.getOp().isSideEffecting()) {
          if (binaryExpr.getLhs() instanceof VariableIdentifierExpr
              && ((VariableIdentifierExpr) binaryExpr.getLhs()).getName()
              .equals(loopCounterName)) {
            foundModification = true;
          }
        }
      }

      public boolean modifiesLoopCounter() {
        visit(forStmt.getBody());
        return foundModification;
      }

    }.modifiesLoopCounter();
  }

  private static boolean areDirectionBoundsAndTestConsistent(boolean loopCounterIncreasing,
                                                             int startValue,
                                                             int endValue,
                                                             BinOp comparison,
                                                             boolean variableBeforeLiteral) {
    List<BinOp> operatorsIfVariableBeforeLiteral =
        loopCounterIncreasing ? Arrays.asList(BinOp.LT, BinOp.LE, BinOp.NE)
            : Arrays.asList(BinOp.GT, BinOp.GT, BinOp.NE);

    List<BinOp> operatorsIfVariableAfterLiteral =
        loopCounterIncreasing ? Arrays.asList(BinOp.GT, BinOp.GT, BinOp.NE)
            : Arrays.asList(BinOp.LT, BinOp.LE, BinOp.NE);

    int shouldBeLower = loopCounterIncreasing ? startValue : endValue;
    int shouldBeUpper = loopCounterIncreasing ? endValue : startValue;

    if (shouldBeUpper <= shouldBeLower) {
      return false;
    }
    if (variableBeforeLiteral) {
      if (!operatorsIfVariableBeforeLiteral.contains(comparison)) {
        return false;
      }
    } else {
      if (!operatorsIfVariableAfterLiteral.contains(comparison)) {
        return false;
      }
    }
    return true;
  }

  private static Optional<String> maybeGetName(Expr expr) {
    if (!(expr instanceof VariableIdentifierExpr)) {
      return Optional.empty();
    }
    return Optional.of(((VariableIdentifierExpr) expr).getName());
  }

  private static Optional<Integer> maybeGetIntegerValue(Expr expr) {
    if (!(expr instanceof IntConstantExpr)) {
      return Optional.empty();
    }
    return Optional.of(Integer.parseInt(((IntConstantExpr) expr).getValue()));
  }

  public static boolean suitableForSplitting(IInjectionPoint injectionPoint) {

    if (!injectionPoint.hasNextStmt()) {
      return false;
    }

    // We cannot split unless the next statement is a for loop
    if (!(injectionPoint.getNextStmt() instanceof ForStmt)) {
      return false;
    }
    return maybeGetLoopSplitInfo((ForStmt) injectionPoint.getNextStmt()).isPresent();
  }

}
