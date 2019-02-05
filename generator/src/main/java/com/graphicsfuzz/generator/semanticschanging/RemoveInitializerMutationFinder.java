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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;

/**
 * Finds mutations such as: int v = w -> int v.
 */
public class RemoveInitializerMutationFinder extends MutationFinderBase<ReplaceDeclInfoMutation> {

  public RemoveInitializerMutationFinder(TranslationUnit tu) {
    super(tu);
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    super.visitVariablesDeclaration(variablesDeclaration);

    if (underForLoopHeader) {
      return;
    }

    if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.CONST)) {
      return;
    }

    for (int i = 0; i < variablesDeclaration.getNumDecls(); ++i) {
      if (variablesDeclaration.getDeclInfo(i).hasInitializer()) {
        final VariableDeclInfo newDeclInfo = variablesDeclaration.getDeclInfo(i).clone();
        newDeclInfo.setInitializer(null);
        addMutation(new ReplaceDeclInfoMutation(variablesDeclaration, i, newDeclInfo));
      }
    }
  }

}
