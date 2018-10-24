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

import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.inliner.Inliner;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class InlinerTest {

  @Test
  public void testInline1() throws Exception {
    final String program = "void foo() {} void main() { foo(); }";
    final String expected = "void foo() {} void main() { { } }";
    final TranslationUnit tu = ParseHelper.parse(program);
    FunctionCallExpr fce = getFunctionCallExprs(tu).get(0);
    Inliner.inline(fce, tu, ShadingLanguageVersion.ESSL_100,
          new IdGenerator());
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testInline2() throws Exception {
    final String program = "int foo() { return 2; } void main() { int x = foo(); }";
    final String expected = "int foo() { return 2; } void main() { int foo_inline_return_value_0; { foo_inline_return_value_0 = 2; } int x = foo_inline_return_value_0; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    FunctionCallExpr fce = getFunctionCallExprs(tu).get(0);
    Inliner.inline(fce, tu, ShadingLanguageVersion.ESSL_100,
          new IdGenerator());
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  @Test
  public void testInline3() throws Exception {
    final String program = ""
          + "float baz(float a, vec2 b) {"
          + "  float c;"
          + "  c = a + b.x;"
          + "  float d;"
          + "  d = c + a + b.y;"
          + "  return d + 2.0;"
          + "}"
          + "void main() {"
          + "  int zz;"
          + "  float x = 2.0;"
          + "  x = baz(float(zz), vec2(1.0, baz(3.0, vec2(x))));"
          + "}";
    final String expected1 = ""
          + "float baz(float a, vec2 b) {"
          + "  float c;"
          + "  c = a + b.x;"
          + "  float d;"
          + "  d = c + a + b.y;"
          + "  return d + 2.0;"
          + "}"
          + "void main() {"
          + "  int zz;"
          + "  float x = 2.0;"
          + "  float baz_inline_return_value_0;"
          + "  {"
          + "    float a = float(zz);"
          + "    vec2 b = vec2(1.0, baz(3.0, vec2(x)));"
          + "    float c;"
          + "    c = a + b.x;"
          + "    float d;"
          + "    d = c + a + b.y;"
          + "    baz_inline_return_value_0 = d + 2.0;"
          + "  }"
          + "  x = baz_inline_return_value_0;"
          + "}";
    final String expected2 = ""
          + "float baz(float a, vec2 b) {"
          + "  float c;"
          + "  c = a + b.x;"
          + "  float d;"
          + "  d = c + a + b.y;"
          + "  return d + 2.0;"
          + "}"
          + "void main() {"
          + "  int zz;"
          + "  float x = 2.0;"
          + "  float baz_inline_return_value_0;"
          + "  {"
          + "    float a = float(zz);"
          + "    float baz_inline_return_value_1;"
          + "    {"
          + "      float a = 3.0;"
          + "      vec2 b = vec2(x);"
          + "      float c;"
          + "      c = a + b.x;"
          + "      float d;"
          + "      d = c + a + b.y;"
          + "      baz_inline_return_value_1 = d + 2.0;"
          + "    }"
          + "    vec2 b = vec2(1.0, baz_inline_return_value_1);"
          + "    float c;"
          + "    c = a + b.x;"
          + "    float d;"
          + "    d = c + a + b.y;"
          + "    baz_inline_return_value_0 = d + 2.0;"
          + "  }"
          + "  x = baz_inline_return_value_0;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    FunctionCallExpr fce = getFunctionCallExprs(tu).get(0);
    final IdGenerator idGenerator = new IdGenerator();
    Inliner.inline(fce, tu, ShadingLanguageVersion.ESSL_100,
          idGenerator);
    CompareAstsDuplicate.assertEqualAsts(expected1, tu);
    fce = getFunctionCallExprs(tu).get(0);
    Inliner.inline(fce, tu, ShadingLanguageVersion.ESSL_100,
          idGenerator);
    CompareAstsDuplicate.assertEqualAsts(expected2, tu);
  }

  @Test
  public void testInline4() throws Exception {
    final String program = ""
          + "int z = 3;"
          + "int foo(int x, ivec2 y) {"
          + "  z = y[0];"
          + "  if (x > 3) {"
          + "    return y[1];"
          + "  } else {"
          + "    return x;"
          + "  }"
          + "}"
          + ""
          + "void main() {"
          + "  ivec2 h = ivec2(1, 3);"
          + "  h.x = 2 + foo(h.y, h + ivec2(3));"
          + "}";
    final String expected = ""
          + "int z = 3;"
          + "int foo(int x, ivec2 y) {"
          + "  z = y[0];"
          + "  if (x > 3) {"
          + "    return y[1];"
          + "  } else {"
          + "    return x;"
          + "  }"
          + "}"
          + ""
          + "void main() {"
          + "  ivec2 h = ivec2(1, 3);"
          + "  int foo_inline_return_value_0;"
          + "  {"
          + "    int x = h.y;"
          + "    ivec2 y = h + ivec2(3);"
          + "    bool foo_has_returned = false;"
          + "    int foo_return_value;"
          + "    z = y[0];"
          + "    if (x > 3) {"
          + "      {"
          + "        foo_return_value = y[1];"
          + "        foo_has_returned = true;"
          + "      }"
          + "    } else {"
          + "      {"
          + "        foo_return_value = x;"
          + "        foo_has_returned = true;"
          + "      }"
          + "    }"
          + "    foo_inline_return_value_0 = foo_return_value;"
          + "  }"
          + "  h.x = 2 + foo_inline_return_value_0;"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    FunctionCallExpr fce = getFunctionCallExprs(tu).get(0);
    Inliner.inline(fce, tu, ShadingLanguageVersion.ESSL_100,
          new IdGenerator());
    CompareAstsDuplicate.assertEqualAsts(expected, tu);
  }

  private List<FunctionCallExpr> getFunctionCallExprs(TranslationUnit tu) {
    return new StandardVisitor() {
      private List<FunctionCallExpr> functionCallExprs = new ArrayList<FunctionCallExpr>();

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        functionCallExprs.add(functionCallExpr);
        super.visitFunctionCallExpr(functionCallExpr);
      }

      private List<FunctionCallExpr> getFunctionCallExprs(IAstNode node) {
        visit(node);
        return functionCallExprs;
      }
    }.getFunctionCallExprs(tu);
  }

}