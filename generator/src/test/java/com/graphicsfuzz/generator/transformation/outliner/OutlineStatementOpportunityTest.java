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

package com.graphicsfuzz.generator.transformation.outliner;

import static org.junit.Assert.assertEquals;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.util.IdGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class OutlineStatementOpportunityTest {

  private FunctionDefinition fakeFunction;
  private TranslationUnit tu;

  @Before
  public void setUp() {
    fakeFunction = new FunctionDefinition(
        new FunctionPrototype("fake", VoidType.VOID, new ArrayList<>())
        , new BlockStmt(new ArrayList<>(), false));
    tu = new TranslationUnit(null, Arrays.asList(fakeFunction));
  }

  @Test
  public void apply() throws Exception {

    ExprStmt toOutline = new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr("x"),
        new BinaryExpr(new BinaryExpr(new VariableIdentifierExpr("x"),
            new VariableIdentifierExpr("y"), BinOp.MUL), new VariableIdentifierExpr("z"),
            BinOp.ADD), BinOp.ASSIGN));

    Scope fakeScope = new Scope(null);
    fakeScope.add("x", BasicType.VEC2, Optional.empty());
    fakeScope.add("y", BasicType.FLOAT, Optional.empty());
    fakeScope.add("z", BasicType.VEC2, Optional.empty());

    new OutlineStatementOpportunity(toOutline, fakeScope, tu, fakeFunction).apply(new IdGenerator());
    final String expectedDecl = "vec2 " + Constants.OUTLINED_FUNCTION_PREFIX + "0(vec2 x, float y, vec2 z)\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "return x * y + z;\n"
        + "}\n";
    assertEquals(expectedDecl, tu.getTopLevelDeclarations().get(0).getText());
    assertEquals("x = " + Constants.OUTLINED_FUNCTION_PREFIX + "0(x, y, z);\n",
        toOutline.getText());

  }

  @Test
  public void apply2() throws Exception {


    ExprStmt toOutline = new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr("x"),
        new VariableIdentifierExpr("y"),
        BinOp.ASSIGN));

    Scope fakeScope = new Scope(null);
    fakeScope.add("x", BasicType.VEC2, Optional.empty());
    fakeScope.add("y", new QualifiedType(BasicType.VEC2, Arrays.asList(TypeQualifier.UNIFORM)), Optional.empty());

    new OutlineStatementOpportunity(toOutline, fakeScope, tu, fakeFunction).apply(new IdGenerator());

    // "uniform" is not expected in the declaration
    final String expectedDecl = "vec2 " + Constants.OUTLINED_FUNCTION_PREFIX + "0(vec2 y)\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "return y;\n"
        + "}\n";
    assertEquals(expectedDecl, tu.getTopLevelDeclarations().get(0).getText());
    assertEquals("x = " + Constants.OUTLINED_FUNCTION_PREFIX + "0(y);\n",
        toOutline.getText());

  }

  @Test
  public void apply3() throws Exception {

    ExprStmt toOutline = new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr("x"),
        new BinaryExpr(new VariableIdentifierExpr("x"),
            new VariableIdentifierExpr("x"),
            BinOp.ADD),
        BinOp.ASSIGN));

    Scope fakeScope = new Scope(null);
    fakeScope.add("x", BasicType.VEC2, Optional.empty());

    new OutlineStatementOpportunity(toOutline, fakeScope, tu, fakeFunction).apply(new IdGenerator());

    // "uniform" is not expected in the declaration
    final String expectedDecl = "vec2 " + Constants.OUTLINED_FUNCTION_PREFIX + "0(vec2 x)\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "return x + x;\n"
        + "}\n";
    assertEquals(expectedDecl, tu.getTopLevelDeclarations().get(0).getText());
    assertEquals("x = " + Constants.OUTLINED_FUNCTION_PREFIX + "0(x);\n",
        toOutline.getText());

  }

  @Test
  public void apply4() throws Exception {

    ExprStmt toOutline = new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr("x"),
        new BinaryExpr(new VariableIdentifierExpr("x"),
            new VariableIdentifierExpr("x"),
            BinOp.ADD),
        BinOp.ASSIGN));

    Scope fakeScope = new Scope(null);
    fakeScope.add("x", new QualifiedType(BasicType.VEC2, Arrays.asList(TypeQualifier.OUT_PARAM)), Optional.empty());

    new OutlineStatementOpportunity(toOutline, fakeScope, tu, fakeFunction).apply(new IdGenerator());

    // "uniform" is not expected in the declaration
    final String expectedDecl = "vec2 " + Constants.OUTLINED_FUNCTION_PREFIX + "0(vec2 x)\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "return x + x;\n"
        + "}\n";
    assertEquals(expectedDecl, tu.getTopLevelDeclarations().get(0).getText());
    assertEquals("x = " + Constants.OUTLINED_FUNCTION_PREFIX + "0(x);\n",
        toOutline.getText());

  }

  @Test
  public void apply5() throws Exception {

    ExprStmt toOutline = new ExprStmt(new BinaryExpr(
        new VariableIdentifierExpr(OpenGlConstants.GL_POINT_SIZE),
        new BinaryExpr(new VariableIdentifierExpr("x"),
            new VariableIdentifierExpr("x"),
            BinOp.ADD),
        BinOp.ASSIGN));

    Scope fakeScope = new Scope(null);
    fakeScope.add("x", BasicType.FLOAT, Optional.empty());

    new OutlineStatementOpportunity(toOutline, fakeScope, tu, fakeFunction).apply(new IdGenerator());

    final String expectedDecl = "float " + Constants.OUTLINED_FUNCTION_PREFIX + "0(float x)\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "return x + x;\n"
        + "}\n";
    assertEquals(expectedDecl, tu.getTopLevelDeclarations().get(0).getText());
    assertEquals(OpenGlConstants.GL_POINT_SIZE + " = " + Constants.OUTLINED_FUNCTION_PREFIX + "0"
            + "(x);\n",
        toOutline.getText());

  }

}
