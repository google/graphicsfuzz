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

package com.graphicsfuzz.generator.knownvaluegeneration;

public abstract class VariableFact {
  private final Value value;

  /**
   * @param value which a new VariableDeclFact or ParameterDeclFact is representing.
   */
  VariableFact(Value value) {
    this.value = value;
  }

  /**
   * @return a known value of the variable fact.
   */
  public Value getValue() {
    return value;
  }

  /**
   * @return a variable name of this variable fact. This is used by the generator
   *     when it is generating a new variable identifier expression.
   */
  public abstract String getVariableName();
}
