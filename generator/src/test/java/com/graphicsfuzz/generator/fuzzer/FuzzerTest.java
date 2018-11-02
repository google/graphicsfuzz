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

package com.graphicsfuzz.generator.fuzzer;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.ZeroCannedRandom;
import com.graphicsfuzz.generator.util.GenerationParams;
import org.junit.Test;

import static org.junit.Assert.*;

public class FuzzerTest {

  @Test
  public void testStructExprFuzzing() throws Exception {

    final String shader = "" +
        "struct A {" +
        "  int f1;" +
        "  int f2;" +
        "};\n" +
        "struct B {" +
        "   A f1;" +
        "   A f2;" +
        "};" +
        "void main() {" +
        "  int doitWhenYouReachMyUse;" +
        "  doitWhenYouReachMyUse;" +
        "}";

    TranslationUnit tu = ParseHelper.parse(shader);

    new ScopeTreeBuilder() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals("doitWhenYouReachMyUse")) {
          Expr expr = new Fuzzer(new FuzzingContext(currentScope), ShadingLanguageVersion.ESSL_100,
              new ZeroCannedRandom(), GenerationParams.normal(ShaderKind.FRAGMENT, true), "prefix")
              .fuzzExpr(new StructNameType("B"), false, false, 0);
          assertTrue(expr instanceof TypeConstructorExpr);
          // Sanity check a few things about the result
          final TypeConstructorExpr outer = (TypeConstructorExpr) expr;
          assertTrue(outer.getTypename().equals("B"));
          assertTrue(outer.getArg(0) instanceof TypeConstructorExpr);
          assertTrue(outer.getArg(1) instanceof TypeConstructorExpr);
          final TypeConstructorExpr inner0 = (TypeConstructorExpr) outer.getArg(0);
          final TypeConstructorExpr inner1 = (TypeConstructorExpr) outer.getArg(1);
          assertTrue(inner0.getTypename().equals("A"));
          assertTrue(inner1.getTypename().equals("A"));
        }
      }
    }.visit(tu);
  }

}