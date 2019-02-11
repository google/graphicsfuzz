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

package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import java.util.function.Supplier;

public abstract class Node2NodeMutation<NodeT extends IAstNode>
      implements Mutation {

  private final IAstNode parent;
  private final NodeT original;
  private final Supplier<NodeT> replacement;

  /**
   * Represents the opportunity to replace the original child of a node with a new child.  The new
   * child is produced lazily via a Supplier, so that it need not be computed unless the mutation
   * is actually applied.
   * @param parent Parent of the node to be changed.
   * @param original The node to be changed.
   * @param replacement A supplier for the replacement node.
   */
  protected Node2NodeMutation(IAstNode parent, NodeT original, Supplier<NodeT> replacement) {
    assert parent.hasChild(original);
    this.parent = parent;
    this.original = original;
    this.replacement = replacement;
  }

  @Override
  public final void apply() {
    // It is possible that another mutation may have invalidated this mutation.
    if (parent.hasChild(original)) {
      parent.replaceChild(original, replacement.get());
    }
  }

}
