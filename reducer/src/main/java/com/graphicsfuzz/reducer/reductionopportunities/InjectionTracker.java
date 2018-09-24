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

import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

class InjectionTracker {

  private int numEnclosingFuzzedMacros;
  private int numEnclosingDeadCodeInjections;
  private boolean underDeadMacro;

  private Deque<Optional<SwitchCaseStatus>> switchStmts;

  InjectionTracker() {
    this.numEnclosingFuzzedMacros = 0;
    this.numEnclosingDeadCodeInjections = 0;
    this.underDeadMacro = false;
    this.switchStmts = new LinkedList<>();
  }

  boolean underFuzzedMacro() {
    return numEnclosingFuzzedMacros > 0;
  }

  boolean underDeadMacro() {
    return underDeadMacro;
  }

  void enterFuzzedMacro() {
    numEnclosingFuzzedMacros++;
  }

  void exitFuzzedMacro() {
    assert numEnclosingFuzzedMacros > 0;
    numEnclosingFuzzedMacros--;
  }

  void enterDeadMacro() {
    assert !underDeadMacro;
    underDeadMacro = true;
  }

  void exitDeadMacro() {
    assert underDeadMacro;
    underDeadMacro = false;
  }

  void enterDeadCodeInjection() {
    numEnclosingDeadCodeInjections++;
  }

  void exitDeadCodeInjection() {
    assert numEnclosingDeadCodeInjections > 0;
    numEnclosingDeadCodeInjections--;
  }

  boolean enclosedByDeadCodeInjection() {
    return numEnclosingDeadCodeInjections > 0;
  }

  public void enterSwitch(boolean isInjected) {
    switchStmts.addLast(
        isInjected ? Optional.of(SwitchCaseStatus.NO_LABEL_YET) : Optional.empty());
  }

  public void leaveSwitch(boolean isInjected) {
    assert !switchStmts.isEmpty();
    Optional<SwitchCaseStatus> temp = switchStmts.removeLast();
    assert temp.isPresent() == isInjected;
  }

  public void notifySwitchCase(ExprCaseLabel caseLabel) {
    assert !switchStmts.isEmpty();
    Optional<SwitchCaseStatus> status = switchStmts.peekLast();
    if (!status.isPresent()) {
      return;
    }
    if (status.get() == SwitchCaseStatus.NO_LABEL_YET) {
      status = resetSwitchCaseStatus(SwitchCaseStatus.BEFORE_ORIGINAL_CODE);
    }
    if (((IntConstantExpr) caseLabel.getExpr()).getValue().equals("0")) {
      assert status.get() == SwitchCaseStatus.BEFORE_ORIGINAL_CODE;
      resetSwitchCaseStatus(SwitchCaseStatus.IN_ORIGINAL_CODE);
    }
  }

  public void notifySwitchBreak() {
    assert !switchStmts.isEmpty();
    Optional<SwitchCaseStatus> status = switchStmts.peekLast();
    if (!status.isPresent()) {
      return;
    }
    if (status.get() == SwitchCaseStatus.IN_ORIGINAL_CODE) {
      resetSwitchCaseStatus(SwitchCaseStatus.AFTER_ORIGINAL_CODE);
    }
  }

  private Optional<SwitchCaseStatus> resetSwitchCaseStatus(SwitchCaseStatus newStatus) {
    switchStmts.removeLast();
    Optional<SwitchCaseStatus> status = Optional.of(newStatus);
    switchStmts.addLast(status);
    return status;
  }

  public boolean underUnreachableSwitchCase() {
    for (Optional<SwitchCaseStatus> status : switchStmts) {
      if (status.isPresent() && (status.get() == SwitchCaseStatus.BEFORE_ORIGINAL_CODE
          || status.get() == SwitchCaseStatus.AFTER_ORIGINAL_CODE)) {
        return true;
      }
    }
    return false;
  }

}
