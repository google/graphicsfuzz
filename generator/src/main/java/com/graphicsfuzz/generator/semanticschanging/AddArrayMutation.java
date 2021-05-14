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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import java.util.Collections;
import java.util.Optional;

public class AddArrayMutation implements Mutation {

  private final TranslationUnit tu;
  private final String name;
  private final BasicType baseType;
  private final int numElements;

  public AddArrayMutation(TranslationUnit tu, String name, BasicType baseType, int numElements) {
    this.tu = tu;
    this.name = name;
    this.baseType = baseType;
    this.numElements = numElements;
  }

  @Override
  public void apply() {
    tu.addDeclaration(new VariablesDeclaration(
        baseType, new VariableDeclInfo(name,
        new ArrayInfo(Collections.singletonList(
            Optional.of(new IntConstantExpr(Integer.toString(numElements))))),
        null)));
  }

}
