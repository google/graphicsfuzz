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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.stmt.CaseLabel;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;

/**
 * This class removes case labels (including default) that are not nested inside switch statements.
 */
public class RemoveImmediateCaseLabels extends RemoveStatements {

  public RemoveImmediateCaseLabels(IAstNode node) {
    super(item -> item instanceof CaseLabel,
        item -> makeIntConstantExprStmt(), node);
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    // Block visitation: we don't want to remove labels from inside switch statements
  }

}
