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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SideEffectCheckerTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void testAssignmentHasSideEffects() throws Exception {
    assertFalse(SideEffectChecker.isSideEffectFree(new ExprStmt(
        new BinaryExpr(
          new VariableIdentifierExpr("v"),
          new IntConstantExpr("12"),
          BinOp.ASSIGN
    )), ShadingLanguageVersion.ESSL_310, ShaderKind.FRAGMENT));
  }

  @Test
  public void testCountingLoopHasSideEffects() throws Exception {
    assertFalse(SideEffectChecker.isSideEffectFree(new ForStmt(
        new ExprStmt(new BinaryExpr(
            new VariableIdentifierExpr("i"),
            new IntConstantExpr("0"),
            BinOp.ASSIGN
        )),
        new BinaryExpr(
            new VariableIdentifierExpr("i"),
            new IntConstantExpr("10"),
            BinOp.LT),
        new UnaryExpr(
            new VariableIdentifierExpr("i"),
            UnOp.POST_INC
        ), new BlockStmt(Collections.emptyList(), false)),
        ShadingLanguageVersion.ESSL_310, ShaderKind.FRAGMENT));
  }

  @Test
  public void testOutParamHasSideEffects() throws Exception {
    final String shader = "void main() { "
        + "   uint out1;"
        + "   uvec2 out2;"
        + "   uvec3 out3;"
        + "   uvec4 out4;"
        + "   uaddCarry(uint(0), uint(0), out1);"
        + "   uaddCarry(uvec2(0, 0), uvec2(0, 0), out2);"
        + "   uaddCarry(uvec3(0, 0, 0), uvec3(0, 0, 0), out3);"
        + "   uaddCarry(uvec4(0, 0, 0, 0), uvec4(0, 0, 0, 0), out4);"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    tu.setShadingLanguageVersion(ShadingLanguageVersion.ESSL_310);

    new StandardVisitor() {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr expr) {
        assertTrue(expr.getCallee().equals("uaddCarry"));
        assertFalse(SideEffectChecker.isSideEffectFree(expr, ShadingLanguageVersion.ESSL_310,
            ShaderKind.FRAGMENT));
      }
    }.visit(tu);
  }

  @Test
  public void testOutParamModfHasSideEffects() throws Exception {
    final String shader = "void main() { "
        + "  float out1;"
        + "  vec2 out2;"
        + "  vec3 out3;"
        + "  vec4 out4;"
        + "  modf(0.0, out1);"
        + "  modf(vec2(0.0), out2);"
        + "  modf(vec3(0.0), out3);"
        + "  modf(vec4(0.0), out4);"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    tu.setShadingLanguageVersion(ShadingLanguageVersion.ESSL_310);

    new StandardVisitor() {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr expr) {
        assertTrue(expr.getCallee().equals("modf"));
        assertFalse(SideEffectChecker.isSideEffectFree(expr, ShadingLanguageVersion.ESSL_310,
            ShaderKind.FRAGMENT));
      }
    }.visit(tu);
  }

  @Test
  public void testOutParamFrexpHasSideEffects() throws Exception {
    final String shader = "void main() { "
        + "  int out1;"
        + "  ivec2 out2;"
        + "  ivec3 out3;"
        + "  ivec4 out4;"
        + "  frexp(0.0, out1);"
        + "  frexp(vec2(0.0), out2);"
        + "  frexp(vec3(0.0), out3);"
        + "  frexp(vec4(0.0), out4);"
        + "}";

    final TranslationUnit tu = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    tu.setShadingLanguageVersion(ShadingLanguageVersion.ESSL_310);

    new StandardVisitor() {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr expr) {
        assertTrue(expr.getCallee().equals("frexp"));
        assertFalse(SideEffectChecker.isSideEffectFree(expr, ShadingLanguageVersion.ESSL_310,
            ShaderKind.FRAGMENT));
      }
    }.visit(tu);
  }
}
