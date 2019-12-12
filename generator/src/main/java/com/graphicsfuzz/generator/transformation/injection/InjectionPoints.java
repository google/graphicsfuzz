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

package com.graphicsfuzz.generator.transformation.injection;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.LoopStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.util.IRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class InjectionPoints extends ScopeTrackingVisitor {

  private final List<IInjectionPoint> injectionPoints;
  private FunctionDefinition currentFunction;
  private int loopNestingDepth;
  private int switchNestingDepth;
  private final IRandom generator;
  private final Predicate<IInjectionPoint> suitable;

  public InjectionPoints(TranslationUnit tu, IRandom generator,
        Predicate<IInjectionPoint> suitable) {
    this.injectionPoints = new ArrayList<>();
    this.currentFunction = null;
    this.loopNestingDepth = 0;
    this.switchNestingDepth = 0;
    this.generator = generator;
    this.suitable = suitable;
    visit(tu);
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    currentFunction = functionDefinition;
    super.visitFunctionDefinition(functionDefinition);
    currentFunction = null;
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    enterBlockStmt(stmt);
    // This setup ensures that variables declared inside the block are in scope by the time we
    // consider each statement as an injection point.
    // It is a bit ugly because it has to mimic the "enter ... leave" structure of
    // ScopeTrackingVisitor
    for (int i = 0; i < stmt.getNumStmts(); i++) {
      Stmt innerStmt = stmt.getStmt(i);
      if (i == 0 && innerStmt instanceof CaseLabel) {
        // Don't allow injection before first label of case.
        continue;
      }
      maybeAddInjectionPoint(new BlockInjectionPoint(stmt, innerStmt, currentFunction, inLoop(),
          inSwitch(),
          getCurrentScope()));
      visit(innerStmt);
    }
    maybeAddInjectionPoint(new BlockInjectionPoint(stmt, null, currentFunction, inLoop(),
        inSwitch(),
        getCurrentScope()));
    leaveBlockStmt(stmt);
  }

  private boolean inLoop() {
    return loopNestingDepth > 0;
  }

  private boolean inSwitch() {
    return switchNestingDepth > 0;
  }

  private void considerLoopInjectionPoint(LoopStmt loopStmt) {
    if (!(loopStmt.getBody() instanceof BlockStmt)) {
      maybeAddInjectionPoint(new LoopInjectionPoint(loopStmt, currentFunction,
          inSwitch(),
          getCurrentScope()));
    }
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    considerLoopInjectionPoint(doStmt);
    loopNestingDepth++;
    super.visitDoStmt(doStmt);
    loopNestingDepth--;
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    considerLoopInjectionPoint(forStmt);
    loopNestingDepth++;
    super.visitForStmt(forStmt);
    loopNestingDepth--;
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    considerLoopInjectionPoint(whileStmt);
    loopNestingDepth++;
    super.visitWhileStmt(whileStmt);
    loopNestingDepth--;
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    if (!(ifStmt.getThenStmt() instanceof BlockStmt)) {
      maybeAddInjectionPoint(new IfInjectionPoint(ifStmt, true, currentFunction,
          inLoop(),
          inSwitch(),
          getCurrentScope()));
    }
    if (ifStmt.hasElseStmt()) {
      if (!(ifStmt.getElseStmt() instanceof BlockStmt)) {
        maybeAddInjectionPoint(new IfInjectionPoint(ifStmt, false, currentFunction,
            inLoop(),
            inSwitch(),
            getCurrentScope()));
      }
    }
    super.visitIfStmt(ifStmt);
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    // We cannot inject immediately inside a switch statement.
    switchNestingDepth++;
    super.visitSwitchStmt(switchStmt);
    switchNestingDepth--;
  }

  private void maybeAddInjectionPoint(IInjectionPoint injectionPoint) {
    if (suitable.test(injectionPoint)) {
      injectionPoints.add(injectionPoint);
    }
  }

  public List<IInjectionPoint> getInjectionPoints(Function<IRandom, Boolean> choiceFunction) {
    return injectionPoints.stream().filter(injectionPoint ->
          choiceFunction.apply(generator)).collect(Collectors.toList());
  }

  public List<IInjectionPoint> getAllInjectionPoints() {
    return Collections.unmodifiableList(injectionPoints);
  }

}
