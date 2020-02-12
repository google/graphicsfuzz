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

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;

public class VariableDeclInfo implements IAstNode {

  private String name;
  private ArrayInfo arrayInfo;
  private Initializer initializer;

  public VariableDeclInfo(String name, ArrayInfo arrayInfo, Initializer initializer) {
    this.name = name;
    this.arrayInfo = arrayInfo;
    this.initializer = initializer;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get array info.
   *
   * @return array info of the variable declaration
   */
  public ArrayInfo getArrayInfo() {
    return arrayInfo;
  }

  /**
   * Get the current initializer.
   *
   * @return Current initializer, null if no initializer is set
   */
  public Initializer getInitializer() {
    return initializer;
  }

  /**
   * Check whether variable declaration includes array information.
   *
   * @return boolean whether array information exists
   */
  public boolean hasArrayInfo() {
    return getArrayInfo() != null;
  }

  /**
   * Check whether variable declaration includes initializer.
   *
   * @return boolean whether initializer exists
   */
  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  /**
   * Sets a new initializer.
   *
   * @param initializer Initializer to use
   */
  public void setInitializer(Initializer initializer) {
    this.initializer = initializer;
  }

  /**
   * Removes current initializer.
   */
  public void removeInitializer() {
    this.initializer = null;
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitVariableDeclInfo(this);
  }

  @Override
  public VariableDeclInfo clone() {
    return new VariableDeclInfo(name, arrayInfo == null ? null : arrayInfo.clone(),
        initializer == null ? null : initializer.clone());
  }

}
