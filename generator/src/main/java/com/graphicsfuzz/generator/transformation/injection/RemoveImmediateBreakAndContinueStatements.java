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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import java.util.Optional;

/**
 * This class removes break and continue statements that are not nested inside loops.
 */
public class RemoveImmediateBreakAndContinueStatements extends RemoveStatements {

  public RemoveImmediateBreakAndContinueStatements(IAstNode node) {
    super(item -> item instanceof BreakStmt || item instanceof ContinueStmt,
        item -> Optional.of(new NullStmt()), node);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    // Block visitation: we don't want to remove break and continue statements from inside a loop
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    // Block visitation: we don't want to remove break and continue statements from inside a loop
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    // Block visitation: we don't want to remove break and continue statements from inside a loop
  }

}
