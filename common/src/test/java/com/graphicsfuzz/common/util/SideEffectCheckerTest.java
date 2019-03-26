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

import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnOp;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.*;

public class SideEffectCheckerTest {

  @Test
  public void testAssignmentHasSideEffects() throws Exception {
    assertFalse(SideEffectChecker.isSideEffectFree(new ExprStmt(
        new BinaryExpr(
          new VariableIdentifierExpr("v"),
          new IntConstantExpr("12"),
          BinOp.ASSIGN
    )), ShadingLanguageVersion.ESSL_310));
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
        ShadingLanguageVersion.ESSL_310));
  }

}
