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

/**
 * A helper class for that tracks things related to code injections, such as whether traversal is
 * inside a "FUZZED" macro, or inside a dead code injection.
 */
class InjectionTracker {

  // The number of "FUZZED" macros within which the current traversal point is nested.
  private int numEnclosingFuzzedMacros;

  // The number of dead conditionals in which the current traversal point is nested.
  private int numEnclosingDeadCodeInjections;

  // A stack corresponding to all the switch statements in which the current traversal point is
  // nested.  An empty stack entry indicates that the switch statement was not injected.  Otherwise,
  // the stack entry indicates where traversal of the switch statement's case labels is in relation
  // to the original code that the switch statement wraps.
  private Deque<Optional<SwitchTraversalStatus>> switchStmts;

  InjectionTracker() {
    this.numEnclosingFuzzedMacros = 0;
    this.numEnclosingDeadCodeInjections = 0;
    this.switchStmts = new LinkedList<>();
  }

  /**
   * Indicates whether the current traversal point is inside any "FUZZED" macro.
   * @return True if and only if the current traversal point is inside a "FUZZED" macro.
   */
  boolean underFuzzedMacro() {
    return numEnclosingFuzzedMacros > 0;
  }

  /**
   * Informs the tracker that a "FUZZED" macro has been entered.
   */
  void enterFuzzedMacro() {
    numEnclosingFuzzedMacros++;
  }

  /**
   * Informs the tracker that a "FUZZED" macro has been exited.
   */
  void exitFuzzedMacro() {
    assert numEnclosingFuzzedMacros > 0;
    numEnclosingFuzzedMacros--;
  }

  /**
   * Indicates whether the current traversal point is inside any dead code injection.
   * @return True if and only if the current traversal point is inside a dead code injection.
   */
  boolean enclosedByDeadCodeInjection() {
    return numEnclosingDeadCodeInjections > 0;
  }

  /**
   * Informs the tracker that a dead code injection has been entered.
   */
  void enterDeadCodeInjection() {
    numEnclosingDeadCodeInjections++;
  }

  /**
   * Informs the tracker that a dead code injection has been exited.
   */
  void exitDeadCodeInjection() {
    assert numEnclosingDeadCodeInjections > 0;
    numEnclosingDeadCodeInjections--;
  }

  /**
   * Informs the tracker that a switch statement has been entered.
   * @param isInjected indicates whether the switch statement is part of the original shader, or one
   *                   that has been injected.
   */
  void enterSwitch(boolean isInjected) {
    // If the switch statement is not injected, add an empty element to the stack of switch
    // statement statuses.  Otherwise add a status indicating that no case label for this switch
    // statement has been seen yet.
    switchStmts.addLast(
        isInjected ? Optional.of(SwitchTraversalStatus.NO_LABEL_YET) : Optional.empty());
  }

  /**
   * Informs the tracker that a switch statement has been exited.
   * @param isInjected indicates whether the switch statement is part of the original shader, or one
   *                   that has been injected.  It is just used for coherence-checking purposes
   *                   here.
   */
  void leaveSwitch(boolean isInjected) {
    assert !switchStmts.isEmpty();
    Optional<SwitchTraversalStatus> temp = switchStmts.removeLast();
    assert temp.isPresent() == isInjected;
  }

  /**
   * Informs the tracker that a new case label of the current switch statement has been encountered.
   */
  void notifySwitchCase(ExprCaseLabel caseLabel) {
    assert !switchStmts.isEmpty();
    final Optional<SwitchTraversalStatus> status = switchStmts.peekLast();
    if (!status.isPresent()) {
      // An empty status indicates that the switch statement was not injected, so we don't need to
      // record anything in relation to this case label.
      return;
    }
    // A case label of 0 indicates that this is the *original* code that the switch statement wraps.
    if (((IntConstantExpr) caseLabel.getExpr()).getValue().equals("0")) {
      resetSwitchCaseStatus(SwitchTraversalStatus.IN_ORIGINAL_CODE);
    } else if (status.get() == SwitchTraversalStatus.NO_LABEL_YET) {
      // This is the first case label in the statement, and we know that it is not the label
      // corresponding to the original code.
      resetSwitchCaseStatus(SwitchTraversalStatus.OUTSIDE_ORIGINAL_CODE);
    }
    // We otherwise leave the switch case status for the current switch statement unchanged: moving
    // from original code to after original code is handled by tracking switch statement breaks.
  }

  /**
   * Informs the tracker that a break from a switch statement has been encountered.
   */
  void notifySwitchBreak() {
    assert !switchStmts.isEmpty();
    final Optional<SwitchTraversalStatus> status = switchStmts.peekLast();
    if (!status.isPresent()) {
      // An empty status indicates that the switch statement was not injected, so we don't need to
      // record anything in relation to this break.
      return;
    }
    if (status.get() == SwitchTraversalStatus.IN_ORIGINAL_CODE) {
      // Before the break, we were in the original code that the switch statement wraps, so after
      // the break we are no longer in that code.
      resetSwitchCaseStatus(SwitchTraversalStatus.OUTSIDE_ORIGINAL_CODE);
    }
  }

  /**
   * Indicates whether traversal is under an unreachable case label of some switch statement.
   * @return True if and only if the current traversal point is unreachable due to being under an
   *         unreachable switch case label.
   */
  boolean underUnreachableSwitchCase() {
    // Consider the statuses of all switch statements enclosing this traversal point.
    for (Optional<SwitchTraversalStatus> status : switchStmts) {
      // Check whether we are under an unreachable case label for the current switch statement.
      if (status.isPresent() && (status.get() == SwitchTraversalStatus.OUTSIDE_ORIGINAL_CODE)) {
        // We are - so the current traversal point is unreachable.
        return true;
      }
    }
    return false;
  }

  private void resetSwitchCaseStatus(SwitchTraversalStatus newStatus) {
    switchStmts.removeLast();
    switchStmts.addLast(Optional.of(newStatus));
  }

}
