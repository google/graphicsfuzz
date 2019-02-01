/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;

/**
 * Captures an opportunity to remove a statement, and allows the opportunity to be applied.
 */
public class RemoveStmt implements Transformation {

  private final BlockStmt parent;
  private final Stmt child;

  RemoveStmt(BlockStmt parent, Stmt child) {
    assert parent.hasChild(child);
    this.parent = parent;
    this.child = child;
  }

  @Override
  public void apply() {
    parent.removeStmt(child);
  }

}
