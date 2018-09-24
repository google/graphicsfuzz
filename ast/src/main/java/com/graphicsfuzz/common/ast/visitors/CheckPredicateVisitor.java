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

package com.graphicsfuzz.common.ast.visitors;

import com.graphicsfuzz.common.ast.IAstNode;

public abstract class CheckPredicateVisitor extends StandardVisitor {

  private boolean predicateHolds;

  public CheckPredicateVisitor() {
    this.predicateHolds = false;
  }

  public final void predicateHolds() {
    assert !predicateHolds;
    predicateHolds = true;
    throw new AbortVisitationException();
  }

  public final boolean test(IAstNode node) {
    try {
      visit(node);
    } catch (AbortVisitationException exception) {
      // Good: visitation exited early
    }
    return predicateHolds;
  }

}
