/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class InterfaceBlockReductionOpportunity extends AbstractReductionOpportunity {

  private final TranslationUnit translationUnit;
  private final InterfaceBlock interfaceBlock;

  public InterfaceBlockReductionOpportunity(TranslationUnit translationUnit,
                                            InterfaceBlock interfaceBlock,
                                            VisitationDepth depth) {
    super(depth);
    this.translationUnit = translationUnit;
    this.interfaceBlock = interfaceBlock;
  }

  @Override
  void applyReductionImpl() {
    translationUnit.removeTopLevelDeclaration(interfaceBlock);
  }

  @Override
  public boolean preconditionHolds() {
    return translationUnit.getTopLevelDeclarations().contains(interfaceBlock);
  }
}
