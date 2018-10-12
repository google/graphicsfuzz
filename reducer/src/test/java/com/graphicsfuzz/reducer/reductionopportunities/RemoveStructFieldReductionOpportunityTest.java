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

import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

public class RemoveStructFieldReductionOpportunityTest {

  @Test
  public void applyReduction() throws Exception {

    StructDefinitionType bar = new StructDefinitionType(new StructNameType("bar"),
        Arrays.asList("x", "y"),
        Arrays.asList(BasicType.VEC2, BasicType.VEC3));

    StructDefinitionType foo = new StructDefinitionType(new StructNameType("foo"),
        Arrays.asList("a", "b", "c"),
        Arrays.asList(BasicType.FLOAT, bar.getStructNameType(), bar.getStructNameType()));

    Expr barConstructor = new TypeConstructorExpr("bar", Arrays.asList(
        new TypeConstructorExpr("vec2", Arrays.asList(new FloatConstantExpr("0.0"),
            new FloatConstantExpr("0.0"))),
        new TypeConstructorExpr("vec3", Arrays.asList(new FloatConstantExpr("0.0"),
            new FloatConstantExpr("0.0"), new FloatConstantExpr("0.0")))));

    Expr fooConstructor = new TypeConstructorExpr("foo",
        Arrays.asList(new FloatConstantExpr("1.0"),
            barConstructor.clone(), barConstructor.clone()));

    VariableDeclInfo v1 = new VariableDeclInfo("v1", null,
        new ScalarInitializer(fooConstructor.clone()));
    VariableDeclInfo v2 = new VariableDeclInfo("v2", null,
        new ScalarInitializer(fooConstructor.clone()));

    DeclarationStmt declarationStmt = new DeclarationStmt(new VariablesDeclaration(foo.getStructNameType(),
        Arrays.asList(v1, v2)));

    BlockStmt b = new BlockStmt(
        Arrays.asList(
            declarationStmt), false);

    assertEquals("foo v1 = foo(1.0, bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)), bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0))), v2 = foo(1.0, bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)), bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)));\n",
        getString(declarationStmt));

    IReductionOpportunity ro1 = new RemoveStructFieldReductionOpportunity(foo, "a", b,
        new VisitationDepth(0));
    IReductionOpportunity ro2 = new RemoveStructFieldReductionOpportunity(bar, "x", b,
        new VisitationDepth(0));
    IReductionOpportunity ro3 = new RemoveStructFieldReductionOpportunity(foo, "c", b,
        new VisitationDepth(0));

    assertEquals(2, bar.getNumFields());
    assertEquals(3, foo.getNumFields());

    ro1.applyReduction();
    assertEquals("foo v1 = foo(bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)), bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0))), v2 = foo(bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)), bar(vec2(0.0, 0.0), vec3(0.0, 0.0, 0.0)));\n",
        getString(declarationStmt));

    assertEquals(2, bar.getNumFields());
    assertEquals(2, foo.getNumFields());

    ro2.applyReduction();
    assertEquals("foo v1 = foo(bar(vec3(0.0, 0.0, 0.0)), bar(vec3(0.0, 0.0, 0.0))), v2 = foo(bar(vec3(0.0, 0.0, 0.0)), bar(vec3(0.0, 0.0, 0.0)));\n",
        getString(declarationStmt));

    assertEquals(1, bar.getNumFields());
    assertEquals(2, foo.getNumFields());

    ro3.applyReduction();
    assertEquals("foo v1 = foo(bar(vec3(0.0, 0.0, 0.0))), v2 = foo(bar(vec3(0.0, 0.0, 0.0)));\n",
        getString(declarationStmt));

    assertEquals(1, bar.getNumFields());
    assertEquals(1, foo.getNumFields());

  }

  private String getString(DeclarationStmt declarationStmt) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    new PrettyPrinterVisitor(new PrintStream(output)).visit(declarationStmt);
    return new String(output.toByteArray(), StandardCharsets.UTF_8);
  }

}