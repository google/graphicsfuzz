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
     * Size, set after folding.
     */
    private Optional<Integer> constantSize;
    /**
     * Original size expression before folding, for pretty printing.
     */
    private Optional<Expr> sizeExpr;

    /**
     * "originalSize" is the expression in the original shader representing the array size,
     * and is empty if no size was given. If "originalSize" is present, constant folding can
     * be used to turn the expression into an integer value, which can be stored in
     * "constantSize". It is useful to keep "originalSize" around to allow the shader to
     * be pretty-printed in its original form.
     *
     * @param originalSize Array size expression
     */
    public ArrayInfo(Expr originalSize) {
        this.constantSize = Optional.empty();
        this.sizeExpr = Optional.of(originalSize);
    }

    /**
     * Private constructor for cloning, needed since the constant size expression may have been
     * folded by the time the expression is cloned.
     *
     * @param constantSize Possible constant-folded size
     * @param originalSize Original size, for pretty printing
     */
    private ArrayInfo(Optional<Integer> constantSize, Optional<Expr> originalSize) {
        this.constantSize = constantSize;
        this.sizeExpr = originalSize;
    }

    /**
     * Default constructor, for arrays with no size definition.
     */
    public ArrayInfo() {
        this.constantSize = Optional.empty();
        this.sizeExpr = Optional.empty();
    }

    @Override
    public void accept(IAstVisitor visitor) {
        visitor.visitArrayInfo(this);
    }

    @Override
    public ArrayInfo clone() {
        return new ArrayInfo(constantSize, sizeExpr.flatMap(item -> Optional.of(item.clone())));
    }

    /**
     * Get the constant size.
     * If constant folding was not performed yet (for example, the AST is stil being built) or if
     * the array has no size declaration, calling this function will throw an exception.
     *
     * @return Integer value of the array size
     * @throws UnsupportedLanguageFeatureException if folding was not done
     */
    public Integer getConstantSize() throws UnsupportedLanguageFeatureException {
        if (hasConstantSize()) {
            return constantSize.get();
        }
        throw new UnsupportedLanguageFeatureException("Not a constant expression");
    }

    /**
     * Get the original expression for pretty printing.
     *
     * @return Original, non-folded size expression
     */
    public Expr getSizeExpr() {
        return sizeExpr.get();
    }

    /**
     * Query for whether this array has a size definition.
     *
     * @return true if size definition is available
     */
    public boolean hasConstantSize() {
        return constantSize.isPresent();
    }

    /**
     * Query whether this array has a size expression.
     *
     * @return Size expression
     */
    public boolean hasSizeExpr() {
        return sizeExpr.isPresent();
    }

    /**
     * Requires that there is a constant size, and sets the size expression to a constant expression
     * of exactly this size.
     */
    public void resetSizeExprToConstant() {
        assert hasConstantSize();
        this.sizeExpr = Optional.of(new IntConstantExpr(Integer.toString(getConstantSize())));
    }

    /**
     * Set constant expression after folding.
     *
     * @param foldedSize Completely folded size
     */
    public void setConstantSizeExpr(int foldedSize) {
        constantSize = Optional.of(foldedSize);
    }

}
