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
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.generator.mutateapi.MutationFinderBase;
import java.util.ArrayList;
import java.util.List;

public class AddArrayMutationFinder extends MutationFinderBase<AddArrayMutation> {

  private static final int MAX_ARRAYS = 10;
  private static final int MAX_ARRAY_SIZE = 50;

  private final IRandom generator;
  private int numExistingArrays;

  public AddArrayMutationFinder(TranslationUnit tu, IRandom generator) {
    super(tu);
    this.generator = generator;
    this.numExistingArrays = 0;
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {
    super.visitTranslationUnit(translationUnit);
    if (numExistingArrays < MAX_ARRAYS) {
      final List<BasicType> baseTypes = new ArrayList<>();
      // TODO: add more types if the translation unit's shading languge allows it.
      baseTypes.addAll(BasicType.allGenTypes());
      baseTypes.add(BasicType.INT);
      baseTypes.addAll(BasicType.allSquareMatrixTypes());
      addMutation(new AddArrayMutation(translationUnit, "_ADDED_ARRAY_" + numExistingArrays,
          baseTypes.get(generator.nextInt(baseTypes.size())),
          generator.nextPositiveInt(MAX_ARRAY_SIZE)));
    }
  }

  @Override
  public void visitArrayInfo(ArrayInfo arrayInfo) {
    super.visitArrayInfo(arrayInfo);
    if (atGlobalScope()) {
      numExistingArrays++;
    }
  }

}
