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

package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.typing.Scope;

public class VoidType extends BuiltinType {

    public static final VoidType VOID = new VoidType();

    private VoidType() {
        // VoidType is a singleton
    }

    @Override
    public void accept(IAstVisitor visitor) {
        visitor.visitVoidType(this);
    }

    @Override
    public Expr getCanonicalConstant(Scope scope) {
        // Sanity-check that there is indeed no canonical constant.
        assert !hasCanonicalConstant(scope);
        throw new RuntimeException("No canonical constant for " + this);
    }

    @Override
    public boolean hasCanonicalConstant(Scope unused) {
        return false;
    }

    @Override
    public String toString() {
        return "void";
    }
}
