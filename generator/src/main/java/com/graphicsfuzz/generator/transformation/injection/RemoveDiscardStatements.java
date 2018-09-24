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
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import java.util.Optional;

public class RemoveDiscardStatements extends RemoveStatements {

  public RemoveDiscardStatements(IAstNode node) {
    super(item -> item instanceof DiscardStmt,
        item -> Optional.of(NullStmt.INSTANCE), node);
  }

}
