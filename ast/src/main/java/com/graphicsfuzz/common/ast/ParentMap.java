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

package com.graphicsfuzz.common.ast;

import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class ParentMap extends StandardVisitor implements IParentMap {

  private Map<IAstNode, IAstNode> childToParent;

  ParentMap(IAstNode root) {
    childToParent = new HashMap<>();
    visit(root);
  }

  @Override
  public boolean hasParent(IAstNode node) {
    return childToParent.containsKey(node);
  }

  @Override
  public IAstNode getParent(IAstNode node) {
    return childToParent.get(node);
  }

  @Override
  protected <T extends IAstNode> void visitChildFromParent(Consumer<T> visitorMethod, T child,
      IAstNode parent) {
    super.visitChildFromParent(visitorMethod, child, parent);
    // TODO(279): right now there are deliberately cases where a child can have a non-unique parent.
    // We may want to reconsider this.
    assert child instanceof Type
        || !childToParent.containsKey(child) : "There should be no "
        + "aliasing in the AST with the exception of types; found multiple parents for '" + child
        + "' which has class " + child.getClass() + ".";
    childToParent.put(child, parent);
  }

}
