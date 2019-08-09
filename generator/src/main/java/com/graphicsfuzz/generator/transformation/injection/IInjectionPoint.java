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

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.typing.Scope;

public interface IInjectionPoint {

  /**
   * Injects a statement at the injection point.
   *
   * @param stmt The statement to be injected
   */
  void inject(Stmt stmt);

  /**
   * Returns the statement located just after the injection point.
   *
   * @return Statement just after the injection point, or null if there is none
   */
  Stmt getNextStmt();

  /**
   * Returns true if and only if there is a statement following the injection point.
   *
   * @return true iff there is a statement after the injection point
   */
  boolean hasNextStmt();

  /**
   * Replaces the statement located just after the injection point with the given statement.
   *
   * @param stmt The statement with which the statement following the injection point should be
   *             replaced
   * @throws UnsupportedOperationException thrown if there is no next statement
   */
  void replaceNext(Stmt stmt);

  /**
   * Determines whether the injection point is located inside a syntactic loop.
   *
   * @return true if and only if the injection point is inside a syntactic loop
   */
  boolean inLoop();

  /**
   * Determines whether the injection point is located inside a switch statement.
   *
   * @return true if and only if the injection point is inside a switch statement.
   */
  boolean inSwitch();

  /**
   * Returns the function enclosing the injection point.
   *
   * @return the function enclosing the injection point
   */
  FunctionDefinition getEnclosingFunction();

  /**
   * Gives the variables that are in scope at the point of injection.
   *
   * @return Scope at injection point
   */
  Scope scopeAtInjectionPoint();

}
