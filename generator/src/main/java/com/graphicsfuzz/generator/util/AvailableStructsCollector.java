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

package com.graphicsfuzz.generator.util;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AvailableStructsCollector extends ScopeTreeBuilder {

  private final List<StructDefinitionType> structDefinitionTypes;
  private final IAstNode donorFragment;

  public AvailableStructsCollector(TranslationUnit donor, IAstNode donorFragment) {
    this.structDefinitionTypes = new ArrayList<>();
    this.donorFragment = donorFragment;
    visit(donor);
  }

  @Override
  public void visit(IAstNode node) {
    if (node == donorFragment) {
      assert structDefinitionTypes.isEmpty();
      for (String structName : currentScope.namesOfAllStructDefinitionsInScope()) {
        structDefinitionTypes.add(currentScope.lookupStructName(structName));
      }
    }
    super.visit(node);
  }

  public List<StructDefinitionType> getStructDefinitionTypes() {
    return Collections.unmodifiableList(structDefinitionTypes);
  }

}
