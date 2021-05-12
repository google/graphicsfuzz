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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ArrayInfo implements IAstNode {

  /**
   * Size per dimension, set after folding.
   */
  private List<Optional<Integer>> constantSizes;
  /**
   * Original size expression per dimension before folding, for pretty printing.
   */
  private List<Optional<Expr>> sizeExprs;

  /**
   * "sizeExprs" are the expressions in the original shader representing the array size, per
   * dimension. The outermost dimension can be empty if no size is given. If an original size
   * is present, constant folding can be used to turn the expression into an integer value, which
   * can be stored in "constantSize". It is useful to keep "originalSize" around to allow the shader
   * to be pretty-printed in its original form.
   *
   * @param sizeExprs Array size expressions
   */
  public ArrayInfo(List<Optional<Expr>> sizeExprs) {
    this.constantSizes = new ArrayList<>();
    for (int i = 0; i < sizeExprs.size(); i++) {
      if (i > 0 && !sizeExprs.get(i).isPresent()) {
        throw new IllegalArgumentException("Only the outer-most dimension of an array type can be"
            + " absent");
      }
      this.constantSizes.add(Optional.empty());
    }
    this.sizeExprs = new ArrayList<>(sizeExprs);
  }

  // Private constructor to support cloning.
  private ArrayInfo(List<Optional<Integer>> constantSizes, List<Optional<Expr>> sizeExprs) {
    this.constantSizes = new ArrayList<>(constantSizes);
    this.sizeExprs = new ArrayList<>(sizeExprs);
  }

  /**
   * Query the dimensionality of the array.
   *
   * @return The dimensionality of the array
   */
  public int getDimensionality() {
    assert sizeExprs.size() == constantSizes.size();
    return sizeExprs.size();
  }

  /**
   * Query for whether this array has a known constant size in the given dimension.
   *
   * @param dimension The dimension to be queried
   * @return true if size definition is available
   */
  public boolean hasConstantSize(int dimension) {
    return constantSizes.get(dimension).isPresent();
  }

  /**
   * Query whether this array has a size expression in the given dimension.
   *
   * @param dimension The dimension to be queried
   * @return Size expression
   */
  public boolean hasSizeExpr(int dimension) {
    return sizeExprs.get(dimension).isPresent();
  }

  /**
   * Get the constant size in the given dimension.
   * If constant folding was not performed yet (for example, the AST is still being built) or if
   * the array has no size declaration, calling this function will throw an exception.
   *
   * @param dimension The dimension to be queried
   * @return Integer value of the array size
   * @throws UnsupportedLanguageFeatureException if folding was not done
   */
  public Integer getConstantSize(int dimension) {
    if (hasConstantSize(dimension)) {
      return constantSizes.get(dimension).get();
    }
    throw new UnsupportedOperationException("Not a constant expression");
  }

  /**
   * Set constant expression after folding, in the given dimension.
   *
   * @param dimension The dimension for which a constant size is to be set
   * @param foldedSize Completely folded size
   */
  public void setConstantSizeExpr(int dimension, int foldedSize) {
    constantSizes.set(dimension, Optional.of(foldedSize));
  }

  /**
   * Get the original size expression in a given dimension, typically for pretty printing.
   *
   * @param dimension The dimension to be queried
   * @return Original, non-folded size expression
   */
  public Expr getSizeExpr(int dimension) {
    return sizeExprs.get(dimension).get();
  }

  /**
   * Requires that there is a constant size, and sets the size expression to a constant expression
   * of exactly this size.
   *
   * @param dimension The dimension to be reset
   */
  public void resetSizeExprToConstant(int dimension) {
    assert hasConstantSize(dimension);
    sizeExprs.set(dimension,
        Optional.of(new IntConstantExpr(Integer.toString(getConstantSize(dimension)))));
  }

  /**
   * Yields an ArrayInfo object for the requested dimension.
   *
   * @param dimension The dimension for which an ArrayInfo object is required
   * @return An ArrayInfo object for the given dimension
   */
  public ArrayInfo getArrayInfoForDimension(int dimension) {
    return new ArrayInfo(Collections.singletonList(constantSizes.get(dimension)),
        Collections.singletonList(sizeExprs.get(dimension)
            .flatMap(expr -> Optional.of(expr.clone()))));
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayInfo(this);
  }

  @Override
  public ArrayInfo clone() {
    return new ArrayInfo(constantSizes, sizeExprs
        .stream()
        .map(item -> item.flatMap(expr -> Optional.of(expr.clone()))).collect(Collectors.toList()));
  }

}
