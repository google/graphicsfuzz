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

import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public interface IAstNode extends Cloneable {

  void accept(IAstVisitor visitor);

  IAstNode clone();

  /**
   * Replaces a child of the node with a new child.
   * Only available on selected classes where this has proven to be a sensible operation to perform;
   * throws an exception by default.
   * Developers should add to further classes as and when appropriate.
   *
   * @param child The child to be replaced
   * @param newChild The replacement child
   * @throws ChildDoesNotExistException if there is no such child.
   * @throws UnsupportedOperationException if the operation is not available, or if
   *                                       the new child is not suitable.
   */
  default void replaceChild(IAstNode child, IAstNode newChild) {
    throw new UnsupportedOperationException(
        "Child replacement not supported for nodes of type " + this.getClass());
  }


  /**
   * Checks whether the node has a particular child.
   * Only available on selected classes where this has proven to be a sensible operation to perform;
   * throws an exception by default.
   * Developers should add to further classes as and when appropriate.
   *
   * @param candidateChild The potential child node.
   * @throws UnsupportedOperationException if the operation is not supported.
   */
  default boolean hasChild(IAstNode candidateChild) {
    throw new UnsupportedOperationException(
          "Child querying not supported for nodes of type " + this.getClass());
  }


  /**
   * Uses the pretty printer to turn a node into a text representation.
   * @return Text representation of a node
   */
  default String getText() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (PrintStream stream = new PrintStream(baos)) {
      new PrettyPrinterVisitor(stream).visit(this);
      return baos.toString("UTF8");
    } catch (UnsupportedEncodingException exception) {
      return "<unknown>";
    }
  }

}
