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

package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.Constants;
import java.util.HashSet;
import java.util.Set;

public class AddSwitchMutationFinder extends InjectionPointMutationFinder<AddSwitchMutation> {

  public AddSwitchMutationFinder(TranslationUnit tu, IRandom random,
                                 GenerationParams generationParams) {
    super(tu, random, AddSwitchMutation::suitableForSwitchInjection,
        item -> new AddSwitchMutation(item, random, generationParams,
            tu.getShadingLanguageVersion(),
            new IdGenerator(getIdsAlreadyUsedForAddingSwitches(tu))));
  }

  private static Set<Integer> getIdsAlreadyUsedForAddingSwitches(TranslationUnit tu) {
    final Set<Integer> result = new HashSet<>();
    new StandardVisitor() {

      @Override
      public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
        super.visitVariableDeclInfo(variableDeclInfo);
        final String prefix = Constants.GLF_SWITCH + "_";
        if (variableDeclInfo.getName().startsWith(prefix)) {
          // Name has the form 'prefix|digits' - grab the digits.
          StringBuilder intId = new StringBuilder();
          int index = prefix.length();
          while (Character.isDigit(variableDeclInfo.getName().charAt(index))) {
            intId.append(variableDeclInfo.getName().charAt(index));
            index++;
          }
          result.add(new Integer(intId.toString()));
        }
      }

    }.visit(tu);
    return result;
  }

}
