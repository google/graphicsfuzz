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

package com.graphicsfuzz.common.ast.stmt;

import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class DeclarationStmt extends Stmt {

  private VariablesDeclaration variablesDeclaration;

  public DeclarationStmt(VariablesDeclaration variablesDeclaration) {
    this.variablesDeclaration = variablesDeclaration;
  }

  public VariablesDeclaration getVariablesDeclaration() {
    return variablesDeclaration;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitDeclarationStmt(this);
  }

  @Override
  public DeclarationStmt clone() {
    return new DeclarationStmt(variablesDeclaration.clone());
  }

}
