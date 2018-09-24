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

package com.graphicsfuzz.reducer.reductionopportunities;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import java.util.Arrays;
import org.junit.Test;

public class OutlinedStatementReductionOpportunityTest {

  @Test
  public void applyReduction() throws Exception {
    FunctionDefinition defn = new FunctionDefinition(
        new FunctionPrototype("foo", BasicType.INT,
            Arrays.asList(new ParameterDecl("x", BasicType.INT, null))),
        new BlockStmt(Arrays.asList(
            new ReturnStmt(new VariableIdentifierExpr("x"))), false)
    );
    ExprStmt eBefore = new ExprStmt(new BinaryExpr(new VariableIdentifierExpr("y"),
        new FunctionCallExpr("foo", Arrays.asList(new VariableIdentifierExpr("x"))),
        BinOp.ASSIGN));

    ExprStmt eAfter = eBefore.clone();
    ((BinaryExpr) eAfter.getExpr()).setRhs(new VariableIdentifierExpr("x"));

    new OutlinedStatementReductionOpportunity(eBefore, defn, new VisitationDepth(0))
        .applyReduction();

    assertEquals(eBefore.getText(), eAfter.getText());




  }

}