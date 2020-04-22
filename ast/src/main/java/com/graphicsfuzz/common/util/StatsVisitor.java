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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;

public class StatsVisitor extends StandardVisitor {

  private int statements = 0;
  private int nodes = 0;

  public StatsVisitor(IAstNode node) {
    visit(node);
  }

  @Override
  public void visit(IAstNode node) {
    super.visit(node);
    nodes++;
    if (node instanceof Stmt) {
      statements++;
    }
  }

  public int getNumStatements() {
    return statements;
  }

  public int getNumNodes() {
    return nodes;
  }

}
