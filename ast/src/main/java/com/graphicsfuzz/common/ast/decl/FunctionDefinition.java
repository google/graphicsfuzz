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

package com.graphicsfuzz.common.ast.decl;

import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class FunctionDefinition extends Declaration {

  private FunctionPrototype prototype;
  private BlockStmt body;

  public FunctionDefinition(FunctionPrototype prototype, BlockStmt body) {
    this.prototype = prototype;
    this.body = body;
  }

  public FunctionPrototype getPrototype() {
    return prototype;
  }

  public BlockStmt getBody() {
    return body;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitFunctionDefinition(this);
  }

  @Override
  public FunctionDefinition clone() {
    return new FunctionDefinition(prototype.clone(), body.clone());
  }

}
