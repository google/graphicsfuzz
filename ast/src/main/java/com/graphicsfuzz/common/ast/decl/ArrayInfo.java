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

  private Optional<Expr> size;
  private Optional<Expr> originalSize;

  public ArrayInfo(Expr size) {
    this.size = Optional.of(size);
    this.originalSize = Optional.of(size);
  }

  public ArrayInfo() {
    this.size = Optional.empty();
    this.originalSize = Optional.empty();
  }

  public boolean hasSize() {
    return size.isPresent();
  }

  public Integer getSize() throws UnsupportedLanguageFeatureException {
    if (size.get() instanceof IntConstantExpr) {
      return ((IntConstantExpr)size.get()).getNumericValue();
    }
    throw new UnsupportedLanguageFeatureException("Not a constant expression");
  }

  public Expr getSizeExpr() {
    return size.get();
  }

  public void setSizeExpr(Expr expr) {
    this.size = Optional.of(expr);
  }

  public Expr getOriginalSizeExpr() {
    return originalSize.get();
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitArrayInfo(this);
  }

  @Override
  public ArrayInfo clone() {
    return hasSize() ? new ArrayInfo(getSizeExpr()) : new ArrayInfo();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ArrayInfo && size.equals(((ArrayInfo) obj).size);
  }

  @Override
  public int hashCode() {
    return size.hashCode();
  }

}
