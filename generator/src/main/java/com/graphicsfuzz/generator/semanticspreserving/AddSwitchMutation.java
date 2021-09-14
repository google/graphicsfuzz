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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.util.ContainsTopLevelBreak;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AddSwitchMutation implements Mutation {

  private final IInjectionPoint injectionPoint;
  private final IRandom random;
  private final GenerationParams generationParams;
  private final ShadingLanguageVersion shadingLanguageVersion;
  private final IdGenerator idGenerator;

  public AddSwitchMutation(IInjectionPoint injectionPoint,
                           IRandom random,
                           GenerationParams generationParams,
                           ShadingLanguageVersion shadingLanguageVersion,
                           IdGenerator idGenerator) {
    this.injectionPoint = injectionPoint;
    this.random = random;
    this.generationParams = generationParams;
    this.shadingLanguageVersion = shadingLanguageVersion;
    this.idGenerator = idGenerator;
  }

  @Override
  public void apply() {
    assert suitableForSwitchInjection(injectionPoint);
    final Stmt stmt = injectionPoint.getNextStmt();
    if (stmt instanceof BlockStmt) {
      assert isBlockWithoutTopLevelBreaks(stmt);
      switchify(stmt, injectionPoint.scopeAtInjectionPoint());
      return;
    }
    if (stmt instanceof LoopStmt) {
      assert isBlockWithoutTopLevelBreaks(((LoopStmt) stmt).getBody());
      Scope scope;
      if (stmt instanceof ForStmt) {
        // The statement is a for loop, which might declare new variables.  We thus make a new scope
        // with the existing scope as its parent, and add any declarations introduced by the for
        // loop header to the new scope.  This is important if the declarations in the for loop
        // header shadow existing declarations.
        final ForStmt forStmt = (ForStmt) stmt;
        scope = new Scope(injectionPoint.scopeAtInjectionPoint());
        if (forStmt.getInit() instanceof DeclarationStmt) {
          // The for loop's initializer declares variables, so add each declaration to the scope.
          addVariablesToScope(scope, (DeclarationStmt) forStmt.getInit());
        }
      } else {
        // The statement is not a for loop, so it does not introduce new variables; use the scope
        // associated with the injection point.
        scope = injectionPoint.scopeAtInjectionPoint();
      }
      switchify(((LoopStmt) stmt).getBody(), scope);
      return;
    }
    assert stmt instanceof IfStmt;
    boolean thenBranchCanBeTransformed =
        isBlockWithoutTopLevelBreaks(((IfStmt) stmt).getThenStmt());
    boolean elseBranchCanBeTransformed =
        ((IfStmt) stmt).hasElseStmt()
            && isBlockWithoutTopLevelBreaks(((IfStmt) stmt).getElseStmt());

    if (thenBranchCanBeTransformed && !elseBranchCanBeTransformed) {
      switchify(((IfStmt) stmt).getThenStmt(), injectionPoint.scopeAtInjectionPoint());
      return;
    }
    if (!thenBranchCanBeTransformed && elseBranchCanBeTransformed) {
      switchify(((IfStmt) stmt).getElseStmt(), injectionPoint.scopeAtInjectionPoint());
      return;
    }
    assert thenBranchCanBeTransformed && elseBranchCanBeTransformed;
    while (true) {
      boolean transformedOne = false;
      if (random.nextBoolean()) {
        switchify(((IfStmt) stmt).getThenStmt(), injectionPoint.scopeAtInjectionPoint());
        transformedOne = true;
      }
      if (random.nextBoolean()) {
        switchify(((IfStmt) stmt).getElseStmt(), injectionPoint.scopeAtInjectionPoint());
        transformedOne = true;
      }
      if (transformedOne) {
        return;
      }
    }
  }

  private void switchify(Stmt stmt,
                         Scope scope) {
    assert stmt instanceof BlockStmt;
    BlockStmt block = (BlockStmt) stmt;
    if (block.getNumStmts() == 0) {
      return;
    }

    final int casesBefore = random.nextInt(
        generationParams.getMaxInjectedSwitchCasesBeforeOriginalCode());
    final int casesDuring = random.nextInt(
        generationParams.getMaxInjectedSwitchCasesInOriginalCode());
    final int casesAfter = random.nextInt(
        generationParams.getMaxInjectedSwitchCasesAfterOriginalCode());

    // We will make unreachable switch cases both before and after the reachable switch case.  For
    // this purpose we need a fuzzer object.
    //
    // The scope available to the fuzzer object will initially be a new scope with the scope
    // associated with the block as its parent.
    final Scope scopeForFuzzing = new Scope(scope.shallowClone());
    final Fuzzer stmtFuzzer = new Fuzzer(new FuzzingContext(scopeForFuzzing),
        shadingLanguageVersion,
        random,
        generationParams,
        Constants.GLF_SWITCH + "_" + idGenerator.freshId());

    // This tracks labels that have been used in randomly-generated switch cases, to avoid duplicate
    // labels.
    final List<Integer> usedLabels = new ArrayList<>();

    // First, generate some unreachable switch cases to appear *before* the reachable switch case.
    final List<Stmt> switchBodyStmts = generateUnreachableSwitchContent(casesBefore,
        usedLabels, stmtFuzzer, random);

    // Now add the reachable switch case, sprinkled with some more switch cases that target
    // reachable code.
    switchBodyStmts.addAll(generateReachableSwitchCases(block, casesDuring, usedLabels, random));

    // We also want to generate some switch cases to appear after the 'break' associated with the
    // end of the reachable code.  However, the reachable code may have brought new variables into
    // scope, so we need to add any such variables to the current scope.  This is to ensure that
    // the expression fuzzer does not refer to variables of different types that are shadowed by
    // such new variables.

    // Consider each declaration in the block of reachable code.
    for (Stmt stmtInBlock : block.getStmts()) {
      if (stmtInBlock instanceof DeclarationStmt) {
        addVariablesToScope(scopeForFuzzing, (DeclarationStmt) stmtInBlock);
      }
    }

    // Armed with this updated scope, we are in a position to generate unreachable switch cases
    // that occur after the reachable code.
    switchBodyStmts.addAll(generateUnreachableSwitchContent(casesAfter,
        usedLabels, stmtFuzzer, random));

    // Finish the body of the new switch statement with 'default: 1;', so that (for cleanliness)
    // there is always a non-empty default case.
    switchBodyStmts.add(new DefaultCaseLabel());
    switchBodyStmts.add(new ExprStmt(new IntConstantExpr("1")));

    // The overall switch statement has the form 'switch(0) { ... }'
    final Expr zero =
        new OpaqueExpressionGenerator(random, generationParams, shadingLanguageVersion)
            .makeOpaqueZero(BasicType.INT, false, 0,
                new Fuzzer(new FuzzingContext(scope),
                    shadingLanguageVersion,
                    random, generationParams));

    block.setStmts(Collections.singletonList(new SwitchStmt(new FunctionCallExpr(
        Constants.GLF_SWITCH, zero),
        new BlockStmt(switchBodyStmts, true))));
  }

  private void addVariablesToScope(Scope scope, DeclarationStmt declarationStmt) {
    final VariablesDeclaration variablesDeclaration =
        declarationStmt.getVariablesDeclaration();
    for (VariableDeclInfo declInfo : variablesDeclaration.getDeclInfos()) {
      scope.add(declInfo.getName(),
          Typer.combineBaseTypeAndArrayInfo(variablesDeclaration.getBaseType(),
              declInfo.getArrayInfo()),
          declInfo, variablesDeclaration);
    }
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
    result.add(new BreakStmt());
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

  public static boolean suitableForSwitchInjection(IInjectionPoint injectionPoint) {
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

}
