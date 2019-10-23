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
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.ast.visitors.UnsupportedLanguageFeatureException;
import java.util.Optional;

public class ArrayInfo implements IAstNode {

  /**
   * Size expression, should be IntConstantExpr after folding.
   */
  private Optional<Expr> constantSize;
  /**
   * Original size expression before folding, for prettyprint.
   */
  private Optional<Expr> originalSize;

  /**
   * Constructor, stores the original expression and a clone for constant folding.
   * @param size Array size expression
   */
  public ArrayInfo(Expr size) {
    this.constantSize = Optional.of(size.clone());
    this.originalSize = Optional.of(size);
  }

  /**
   * Private constructor for cloning, needed since the constant size expression may have been
   * folded by the time the expression is cloned.
   * @param constantSize
   * @param originalSize
   */
  private ArrayInfo(Expr constantSize, Expr originalSize) {
    this.constantSize = Optional.of(constantSize.clone());
    this.originalSize = Optional.of(originalSize.clone());
  }

  /**
   * Default constructor, for arrays with no size definition.
   */
  public ArrayInfo() {
    this.constantSize = Optional.empty();
    this.originalSize = Optional.empty();
  }

  /**
   * Query for whether this array has a size definition.
   * @return true if size definition is available
   */
  public boolean hasSize() {
    return constantSize.isPresent();
  }

  /**
   * Get the constant size.
   * Assumes that constant folding has occurred before this is called, so calling this while the
   * AST is being built will likely result in exception.
   * @return Integer value of the array size
   * @throws UnsupportedLanguageFeatureException if folding was not done
   */
  public Integer getConstantSize() throws UnsupportedLanguageFeatureException {
    if (constantSize.get() instanceof IntConstantExpr) {
      return ((IntConstantExpr)constantSize.get()).getNumericValue();
    }
    throw new UnsupportedLanguageFeatureException("Not a constant expression");
  }

  /**
   * Get the size expression, which may be folded
   * @return Size expression
   */
  public Expr getSizeExpr() {
    return constantSize.get();
  }

  /**
   * Set constant expression after folding.
   * @param expr Completely folded expression
   */
  public void setConstantSizeExpr(IntConstantExpr expr) {
    constantSize = Optional.of(expr);
  }

  /**
   * Get the original expression for pretty printing.
   * @return Original, non-folded size expression
   */
  public Expr getOriginalSizeExpr() {
    return originalSize.get();
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayInfo(this);
  }

  @Override
  public ArrayInfo clone() {
    return hasSize() ? new ArrayInfo(getSizeExpr(), getOriginalSizeExpr()) :
        new ArrayInfo();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ArrayInfo && originalSize.equals(((ArrayInfo) obj).originalSize);
  }

  @Override
  public int hashCode() {
    return originalSize.hashCode();
  }

}
