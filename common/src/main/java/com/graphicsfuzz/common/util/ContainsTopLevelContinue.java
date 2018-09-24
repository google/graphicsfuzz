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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;

public final class ContainsTopLevelContinue {

  private ContainsTopLevelContinue() {
    // Utility class
  }

  public static boolean check(Stmt stmt) {
    return new CheckPredicateVisitor() {

      @Override
      public void visitForStmt(ForStmt forStmt) {
        // Block visitation
      }

      @Override
      public void visitDoStmt(DoStmt doStmt) {
        // Block visitation
      }

      @Override
      public void visitWhileStmt(WhileStmt whileStmt) {
        // Block visitation
      }

      @Override
      public void visitContinueStmt(ContinueStmt continueStmt) {
        predicateHolds();
      }

    }.test(stmt);
  }

}
