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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds mutations such as: e -> switch(e){case x: e}.
 */
public class ReplaceBlockStmtsWithSwitchMutationFinder
    extends MutationFinderBase<ReplaceBlockStmtsMutation> {

  private static final int MAX_NUM_SWITCH_CASES = 50;
  private static final int MAX_SWITCH_CASE_LABEL_VALUE = 100;

  protected final Typer typer;
  private final IRandom generator;

  public ReplaceBlockStmtsWithSwitchMutationFinder(TranslationUnit tu,
                                                   IRandom generator) {
    super(tu);
    this.typer = new Typer(tu);
    this.generator = generator;
  }

  @Override
  public void visitBlockStmt(BlockStmt block) {

    if (block.getNumStmts() == 0) {
      return;
    }

    // Avoid having instructions before the first case of the switch
    if (block.getStmt(0) instanceof CaseLabel) {
      super.visitBlockStmt(block);
      return;
    }

    final int noOfSwitchCases = generator.nextInt(MAX_NUM_SWITCH_CASES);

    final List<Integer> caseLabelsPos = new ArrayList<>();
    caseLabelsPos.add(0);
    for (int i = 1; i < noOfSwitchCases; i++) {
      caseLabelsPos.add(generator.nextInt(block.getNumStmts()));
    }
    Collections.sort(caseLabelsPos);

    final List<Stmt> switchBodyStmts = new ArrayList<>();
    final List<Stmt> caseBlockStmts = new ArrayList<>();

    final List<Integer> caseLabels = getCaseLabels(noOfSwitchCases);

    int caseCnt = 0;
    int blockStmtCnt = 0;
    while (blockStmtCnt < block.getNumStmts()) {

      while (caseCnt < noOfSwitchCases && caseLabelsPos.get(caseCnt) == blockStmtCnt) {
        switchBodyStmts.add(chooseExprCaseLabel(caseLabels));
        caseCnt++;
      }

      if (caseCnt == noOfSwitchCases) {
        switchBodyStmts.add(new DefaultCaseLabel());
        caseCnt++;
      }

      while (blockStmtCnt < block.getNumStmts() && (caseCnt >= noOfSwitchCases
          || caseLabelsPos.get(caseCnt) > blockStmtCnt)) {
        caseBlockStmts.add(block.getStmt(blockStmtCnt));
        blockStmtCnt++;
      }

      if (caseBlockStmts.stream()
          .noneMatch(ReplaceBlockStmtsWithSwitchMutationFinder::containsDeclarations)) {
        switchBodyStmts.add(new BlockStmt(caseBlockStmts, false));
      } else {
        switchBodyStmts.addAll(caseBlockStmts);
      }

      caseBlockStmts.clear();
    }


    final SwitchStmt replacement = new SwitchStmt(getSwitchCondition(),
        new BlockStmt(switchBodyStmts, false));

    addMutation(new ReplaceBlockStmtsMutation(block, new ArrayList<>(Arrays.asList(replacement))));

    super.visitBlockStmt(block);

  }

  private Expr getSwitchCondition() {
    final List<String> candidateVariables = new ArrayList<>();
    currentScope.namesOfAllVariablesInScope().stream()
        .filter(item -> currentScope.lookupType(item).getWithoutQualifiers().equals(BasicType.INT))
        .forEach(candidateVariables::add);
    if (candidateVariables.isEmpty()) {
      return new LiteralFuzzer(generator).fuzz(BasicType.INT).get();
    }
    return new VariableIdentifierExpr(candidateVariables.get(
        generator.nextInt(candidateVariables.size())));
  }

  private ExprCaseLabel chooseExprCaseLabel(List<Integer> caseLabels) {
    return new ExprCaseLabel(new IntConstantExpr(String.valueOf(caseLabels.remove(
        generator.nextInt(caseLabels.size())))));
  }

  private List<Integer> getCaseLabels(int noOfSwitchCases) {
    final Set<Integer> labelIntsSet = new HashSet<>();
    for (int i = 0; i < noOfSwitchCases; i++) {
      while (true) {
        if (labelIntsSet.add(generator.nextInt(MAX_SWITCH_CASE_LABEL_VALUE))) {
          break;
        }
      }
    }
    final List<Integer> labelIntsList = new ArrayList<>(labelIntsSet);
    Collections.sort(labelIntsList);
    return labelIntsList;
  }

  private static boolean containsDeclarations(IAstNode node) {
    return new CheckPredicateVisitor() {
      @Override
      public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
        predicateHolds();
      }
    }.test(node);
  }

}
