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
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ContainsTopLevelBreak;
import com.graphicsfuzz.common.util.ContainsTopLevelContinue;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.transformation.ITransformation;
import com.graphicsfuzz.generator.transformation.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.IfInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddWrappingConditionalStmts implements ITransformation {

  public static final String NAME = "add_wrapping_conditional_stmts";
  private int loopVariableCounter = 0;

  private static final int NUM_SORTS_OF_WRAP = 4;

  @Override
  public boolean apply(TranslationUnit tu, TransformationProbabilities probabilities,
        ShadingLanguageVersion shadingLanguageVersion, IRandom generator,
      GenerationParams generationParams) {
    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator,
          AddWrappingConditionalStmts::suitableForWrapping).getInjectionPoints(
          probabilities::wrapStmtInConditional);
    for (IInjectionPoint injectionPoint : injectionPoints) {
      assert suitableForWrapping(injectionPoint);
      Stmt wrapped = wrapStatement(injectionPoint, generator, shadingLanguageVersion,
            generationParams);
      if (injectionPoint instanceof IfInjectionPoint) {
        // Avoid replacing the body of an if with an if, due to dangling else problem
        wrapped = new BlockStmt(Arrays.asList(wrapped), false);
      }
      injectionPoint.replaceNext(wrapped);
    }
    return !injectionPoints.isEmpty();
  }

  private static boolean suitableForWrapping(IInjectionPoint injectionPoint) {
    if (!injectionPoint.hasNextStmt()) {
      return false;
    }
    // We cannot wrap a declaration, as it might be used later; if we wrap it up in a scope
    // this is not going to work.
    if (injectionPoint.getNextStmt() instanceof DeclarationStmt) {
      return false;
    }
    // Cannot have case in nested control flow
    if (injectionPoint.getNextStmt() instanceof CaseLabel) {
      return false;
    }
    if (containsTopLevelBreakOrContinue(injectionPoint.getNextStmt())) {
      return false;
    }
    return true;
  }

  @Override
  public String getName() {
    return NAME;
  }

  private static boolean containsTopLevelBreakOrContinue(Stmt stmt) {
    return ContainsTopLevelBreak.check(stmt)
          || ContainsTopLevelContinue.check(stmt);
  }

  private Stmt wrapStatement(IInjectionPoint injectionPoint, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion, GenerationParams generationParams) {
    Stmt stmt = injectionPoint.getNextStmt();
    if (!(stmt instanceof BlockStmt)) {
      List<Stmt> stmts = new ArrayList<>();
      stmts.add(stmt);
      stmt = new BlockStmt(stmts, true);
    }
    OpaqueExpressionGenerator opaqueExpressionGenerator = new OpaqueExpressionGenerator(generator,
          generationParams, shadingLanguageVersion);
    Fuzzer fuzzer = new Fuzzer(new FuzzingContext(injectionPoint.scopeAtInjectionPoint()),
        shadingLanguageVersion, generator,
          generationParams);
    while (true) {
      // Easy to wrap in if (then or else branch), for loop or while loop.  Less easy with
      // while loop due to continue, so we don't try
      switch (generator.nextInt(NUM_SORTS_OF_WRAP)) {
        case 0: // if(true) { stmt; } else { }
          // Note that we need to add the empty else branch, otherwise the new if statement may
          // absorb the else branch of an enclosing if statement.
          return new IfStmt(
                makeWrappedIfCondition(opaqueExpressionGenerator.makeOpaqueBoolean(true,
                      BasicType.BOOL, false, 0, fuzzer), true),
                stmt, emptyBlock());
        case 1: // if(false) { } else { stmt; }
          return new IfStmt(
                makeWrappedIfCondition(opaqueExpressionGenerator.makeOpaqueBoolean(false,
                      BasicType.BOOL, false, 0, fuzzer), false),
                emptyBlock(), stmt);
        case 2: // for(v = 0; v < 1; v++) { stmt; }
          return makeSingleIterationForStmt(stmt, opaqueExpressionGenerator, fuzzer, generator,
              shadingLanguageVersion);
        case 3: // do { stmt; } while(false);
          if (!shadingLanguageVersion.supportedDoStmt()) {
            continue;
          }
          return new DoStmt(stmt,
                makeWrappedLoopCondition(opaqueExpressionGenerator.makeOpaqueBoolean(false,
                      BasicType.BOOL, false, 0, fuzzer)));
        default:
          throw new RuntimeException("Unreachable");
      }
    }
  }

  private BlockStmt emptyBlock() {
    return new BlockStmt(new ArrayList<>(), true);
  }

  private Stmt makeSingleIterationForStmt(Stmt stmt,
        OpaqueExpressionGenerator opaqueExpressionGenerator,
        Fuzzer fuzzer, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion) {
    boolean up = generator.nextBoolean();
    String loopVariableName = "_injected_loop_counter_" + loopVariableCounter;
    loopVariableCounter++;

    boolean loopBoundsMustBeConst = shadingLanguageVersion.restrictedForLoops();

    Expr start = up ? opaqueExpressionGenerator
          .makeOpaqueZero(BasicType.INT, loopBoundsMustBeConst, 0, fuzzer)
          : opaqueExpressionGenerator
                .makeOpaqueOne(BasicType.INT, loopBoundsMustBeConst, 0, fuzzer);
    DeclarationStmt init = new DeclarationStmt(new VariablesDeclaration(BasicType.INT,
          new VariableDeclInfo(loopVariableName, null, new ScalarInitializer(start))));

    Expr end = up ? opaqueExpressionGenerator
          .makeOpaqueOne(BasicType.INT, loopBoundsMustBeConst, 0, fuzzer)
          : opaqueExpressionGenerator
                .makeOpaqueZero(BasicType.INT, loopBoundsMustBeConst, 0, fuzzer);
    BinOp testOp = generator.nextBoolean() ? (up ? BinOp.LT : BinOp.GT) : BinOp.NE;
    Expr test = new BinaryExpr(new VariableIdentifierExpr(loopVariableName), end, testOp);

    UnOp incOp = up ? UnOp.POST_INC : UnOp.POST_DEC;
    Expr inc = new UnaryExpr(new VariableIdentifierExpr(loopVariableName), incOp);

    return new ForStmt(init, makeWrappedLoopCondition(test), inc, stmt);
  }

  private Expr makeWrappedLoopCondition(Expr expr) {
    return new FunctionCallExpr(Constants.GLF_WRAPPED_LOOP, expr);
  }

  static Expr makeWrappedIfCondition(Expr expr, boolean truth) {
    return new FunctionCallExpr(truth
          ? Constants.GLF_WRAPPED_IF_TRUE
          : Constants.GLF_WRAPPED_IF_FALSE, expr);
  }

}
