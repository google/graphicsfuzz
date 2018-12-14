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

package com.graphicsfuzz.generator.transformation.controlflow;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ContainsTopLevelBreak;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AddSwitchStmts implements ITransformation {

  public static final String NAME = "add_switch_stmts";
  private int applicationId;

  public AddSwitchStmts() {
    applicationId = 0;
  }

  @Override
  public boolean apply(TranslationUnit tu, TransformationProbabilities probabilities,
      ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
      GenerationParams generationParams) {
    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator,
          AddSwitchStmts::suitableForSwitchInjection).getInjectionPoints(
          probabilities::switchify);
    for (IInjectionPoint injectionPoint : injectionPoints) {
      assert suitableForSwitchInjection(injectionPoint);
      wrapStatementInSwitch(injectionPoint, generator, shadingLanguageVersion, generationParams);
    }
    applicationId++;
    return !injectionPoints.isEmpty();
  }

  @Override
  public String getName() {
    return NAME;
  }

  private static boolean suitableForSwitchInjection(IInjectionPoint injectionPoint) {
    if (!injectionPoint.hasNextStmt()) {
      return false;
    }
    final Stmt nextStmt = injectionPoint.getNextStmt();

    // We are interested in blocks, and loops and conditionals with block bodies,
    // as long as there are no top-level breaks.

    if (isBlockWithoutTopLevelBreaks(nextStmt)) {
      return true;
    }

    if (nextStmt instanceof IfStmt) {
      final IfStmt nextIfStmt = (IfStmt) nextStmt;
      if (isBlockWithoutTopLevelBreaks(nextIfStmt.getThenStmt())) {
        return true;
      }
      if (nextIfStmt.hasElseStmt() && isBlockWithoutTopLevelBreaks(nextIfStmt.getElseStmt())) {
        return true;
      }
    }

    if (nextStmt instanceof LoopStmt
          && isBlockWithoutTopLevelBreaks(((LoopStmt) nextStmt).getBody())) {
      return true;
    }

    return false;
  }

  private static boolean isBlockWithoutTopLevelBreaks(Stmt nextStmt) {
    return nextStmt instanceof BlockStmt && !ContainsTopLevelBreak.check(nextStmt);
  }

  private void wrapStatementInSwitch(IInjectionPoint injectionPoint, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    final Stmt stmt = injectionPoint.getNextStmt();
    if (stmt instanceof BlockStmt) {
      assert isBlockWithoutTopLevelBreaks(stmt);
      switchify(stmt, injectionPoint, generator, shadingLanguageVersion, generationParams);
      return;
    }
    if (stmt instanceof LoopStmt) {
      assert isBlockWithoutTopLevelBreaks(((LoopStmt) stmt).getBody());
      switchify(((LoopStmt) stmt).getBody(), injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      return;
    }
    assert stmt instanceof IfStmt;
    boolean thenBranchCanBeTransformed =
          isBlockWithoutTopLevelBreaks(((IfStmt) stmt).getThenStmt());
    boolean elseBranchCanBeTransformed =
          ((IfStmt) stmt).hasElseStmt()
                && isBlockWithoutTopLevelBreaks(((IfStmt) stmt).getElseStmt());

    if (thenBranchCanBeTransformed && !elseBranchCanBeTransformed) {
      switchify(((IfStmt) stmt).getThenStmt(), injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      return;
    }
    if (!thenBranchCanBeTransformed && elseBranchCanBeTransformed) {
      switchify(((IfStmt) stmt).getElseStmt(), injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      return;
    }
    assert thenBranchCanBeTransformed && elseBranchCanBeTransformed;
    while (true) {
      boolean transformedOne = false;
      if (generator.nextBoolean()) {
        switchify(((IfStmt) stmt).getThenStmt(), injectionPoint, generator, shadingLanguageVersion,
              generationParams);
        transformedOne = true;
      }
      if (generator.nextBoolean()) {
        switchify(((IfStmt) stmt).getElseStmt(), injectionPoint, generator, shadingLanguageVersion,
              generationParams);
        transformedOne = true;
      }
      if (transformedOne) {
        return;
      }
    }
  }

  private void switchify(Stmt stmt, IInjectionPoint injectionPoint, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    assert stmt instanceof BlockStmt;
    BlockStmt block = (BlockStmt) stmt;
    if (block.getNumStmts() == 0) {
      return;
    }

    final int casesBefore = generator.nextInt(3);
    final int casesDuring = generator.nextInt(3);
    final int casesAfter = generator.nextInt(3);

    final Fuzzer stmtFuzzer = new Fuzzer(new FuzzingContext(injectionPoint.scopeAtInjectionPoint()
          .shallowClone()),
        shadingLanguageVersion,
          generator,
          generationParams,
          "GLFswitch" + applicationId);

    List<Integer> usedLabels = new ArrayList<>();
    List<Stmt> switchBodyStmts = generateUnreachableSwitchContent(casesBefore,
          usedLabels, stmtFuzzer, generator);
    switchBodyStmts.addAll(generateReachableSwitchCases(block, casesDuring, usedLabels, generator));
    switchBodyStmts.addAll(generateUnreachableSwitchContent(casesAfter,
          usedLabels, stmtFuzzer, generator));
    switchBodyStmts.add(DefaultCaseLabel.INSTANCE);
    switchBodyStmts.add(new ExprStmt(new IntConstantExpr("1")));

    final Expr zero =
          new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
                .makeOpaqueZero(BasicType.INT, false, 0,
                      new Fuzzer(new FuzzingContext(injectionPoint.scopeAtInjectionPoint()),
                          shadingLanguageVersion,
                            generator, generationParams));

    block.setStmts(Arrays.asList(new SwitchStmt(new FunctionCallExpr(Constants.GLF_SWITCH, zero),
          new BlockStmt(switchBodyStmts, true))));
  }

  private List<Stmt> generateReachableSwitchCases(BlockStmt block, int numCases,
        List<Integer> usedLabels, IRandom generator) {

    // Index i of this list stores the case labels to appear before statement i
    List<List<Integer>> stmtIndexToCaseLabels = new ArrayList<>();

    // Initially make the label sets empty
    for (int i = 0; i < block.getNumStmts(); i++) {
      stmtIndexToCaseLabels.add(new ArrayList<>());
    }
    // Label 0, which is what we should actually jump to, is the first label.
    stmtIndexToCaseLabels.get(0).add(0);

    for (int i = 0; i < numCases; i++) {
      stmtIndexToCaseLabels.get(generator.nextInt(block.getNumStmts()))
            .add(getCaseLabel(usedLabels, generator));
    }

    List<Stmt> result = new ArrayList<>();

    for (int i = 0; i < block.getNumStmts(); i++) {
      result.addAll(stmtIndexToCaseLabels.get(i)
            .stream().map(item -> new ExprCaseLabel(new IntConstantExpr(item.toString())))
            .collect(Collectors.toList()));
      result.add(block.getStmt(i));
    }
    result.add(BreakStmt.INSTANCE);
    return result;
  }

  private Integer getCaseLabel(List<Integer> usedLabels, IRandom generator) {
    Integer caseLabel;
    do {
      caseLabel = generator.nextPositiveInt(100);
    } while (usedLabels.contains(caseLabel));
    usedLabels.add(caseLabel);
    return caseLabel;
  }

  private List<Stmt> generateUnreachableSwitchContent(int numCases,
        List<Integer> usedLabels,
        Fuzzer stmtFuzzer,
        IRandom generator) {
    List<Stmt> stmts = new ArrayList<>();
    for (int i = 0; i < numCases; i++) {
      Integer caseLabel = getCaseLabel(usedLabels, generator);
      final Stmt fuzzedStmt = stmtFuzzer.fuzzStmt();
      stmts.add(new ExprCaseLabel(
            new IntConstantExpr(caseLabel.toString())));
      stmts.add(fuzzedStmt);
    }
    return stmts;
  }

}
