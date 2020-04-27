/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;

public class GlobalPrecisionDeclarationReductionOpportunity extends AbstractReductionOpportunity {

  final TranslationUnit translationUnit;
  final PrecisionDeclaration precisionDeclaration;

  public GlobalPrecisionDeclarationReductionOpportunity(TranslationUnit translationUnit,
                                                        PrecisionDeclaration precisionDeclaration,
                                                        VisitationDepth depth) {
    super(depth);
    this.translationUnit = translationUnit;
    this.precisionDeclaration = precisionDeclaration;
  }

  @Override
  void applyReductionImpl() {
    translationUnit.removeTopLevelDeclaration(precisionDeclaration);
  }

  @Override
  public boolean preconditionHolds() {
    return translationUnit.getTopLevelDeclarations().contains(precisionDeclaration);
  }

}
