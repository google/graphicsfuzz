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

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.generator.semanticschanging.LiteralFuzzer;

/**
 * This interface defines an expected Value used by {@link ExpressionGenerator} when
 * generating an expression. It represents a possibly unknown value of some type.
 */
public interface Value {

  /**
   * Indicates whether or not this is an unknown value.
   * @return true if the value is unknown and false otherwise.
   */
  boolean valueIsUnknown();

  /**
   * Gets the type of the underlying value.
   * @return the basic type of the value.
   */
  Type getType();

  /**
   * Provides a literal with the same type as the Value's type, such that all parts of the value
   * that are known will have the expected values, and all other parts will be randomized.
   *
   * @param literalFuzzer a util class used to generate fuzzed expressions.
   * @return the expression that represents the value.
   */
  Expr generateLiteral(LiteralFuzzer literalFuzzer);

}
