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
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.Type;

public class Expr2ArgMutationFinder extends Expr2ExprMutationFinder {


  public Expr2ArgMutationFinder(TranslationUnit tu) {
    super(tu);
  }

  @Override
  protected void visitChildOfExpr(Expr parent, int childIndex) {
    if (underForLoopHeader) {
      return;
    }
    final Type parentType = typer.lookupType(parent);
    if (parentType == null) {
      return;
    }
    Type childType = typer.lookupType(parent.getChild(childIndex));
    if (childType == null) {
      return;
    }
    childType = childType.getWithoutQualifiers();
    if (parentType.equals(childType)) {
      addMutation(new Expr2ExprMutation(
            parentMap.getParent(parent),
            parent,
            parent.getChild(childIndex)));
    }
  }

}
