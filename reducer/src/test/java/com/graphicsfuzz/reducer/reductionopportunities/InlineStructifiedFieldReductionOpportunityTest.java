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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Test;

public class InlineStructifiedFieldReductionOpportunityTest {

  @Test
  public void applyReduction() throws Exception {

    final String innerStructTypeName = makeStructName(0);
    final StructDefinitionType innerStructDefinitionType = new StructDefinitionType(new StructNameType(innerStructTypeName),
        Arrays.asList(
            makeFieldname(0),
            makeFieldname(1),
            makeFieldname(2)),
        Arrays.asList(BasicType.INT, BasicType.FLOAT, BasicType.FLOAT));
    final VariablesDeclaration inner = new VariablesDeclaration(
        innerStructDefinitionType);

    final String outerStructTypeName = makeStructName(1);
    final StructDefinitionType outerStructDefinitionType = new StructDefinitionType(new StructNameType(outerStructTypeName),
        Arrays.asList(
            makeFieldname(0),
            makeFieldname(1)),
        Arrays.asList(innerStructDefinitionType.getStructNameType(), BasicType.FLOAT));
    final VariablesDeclaration outer =
        new VariablesDeclaration(
            outerStructDefinitionType);

    final MemberLookupExpr myOuterF0 = new MemberLookupExpr(new VariableIdentifierExpr("myOuter"),
        makeFieldname(0));
    final MemberLookupExpr myOuterF0F1 = new MemberLookupExpr(
        myOuterF0,
        makeFieldname(1));
    final MemberLookupExpr myOuterF0F2 = new MemberLookupExpr(
        myOuterF0.clone(),
        makeFieldname(2));

    final BlockStmt block = new BlockStmt(
        Arrays.asList(
            new DeclarationStmt(new VariablesDeclaration(outerStructDefinitionType.getStructNameType(),
                new VariableDeclInfo("myOuter", null,
                    new ScalarInitializer(
                        new TypeConstructorExpr(outerStructTypeName,
                            Arrays.asList(
                                new TypeConstructorExpr(innerStructTypeName,
                                    Arrays.asList(
                                        new IntConstantExpr("5"),
                                        new FloatConstantExpr("5.0"),
                                        new FloatConstantExpr("5.2"))),
                                new FloatConstantExpr("4.2")
                            ))
                    )))),
            new ExprStmt(new BinaryExpr(myOuterF0F1, myOuterF0F2, BinOp.ASSIGN))), true);

    assertEquals(2, outerStructDefinitionType.getNumFields());
    assertEquals(makeFieldname(0), outerStructDefinitionType.getFieldName(0));
    assertEquals(makeFieldname(1), outerStructDefinitionType.getFieldName(1));

    TranslationUnit tu
        = new TranslationUnit(
            Optional.empty(),
            Arrays.asList(inner, outer, new FunctionDefinition(
                new FunctionPrototype("foo", VoidType.VOID, new ArrayList<>()),
                block)));

    // Apply an inlining opportunity
    new InlineStructifiedFieldReductionOpportunity(
        outerStructDefinitionType, innerStructDefinitionType, makeFieldname(0), tu,
        new VisitationDepth(0)).applyReduction();

    final String expected =
          "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + Constants.STRUCTIFICATION_STRUCT_PREFIX + "1 myOuter = " + Constants.STRUCTIFICATION_STRUCT_PREFIX + "1(5, 5.0, 5.2, 4.2);\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "myOuter._f0_f1 = myOuter._f0_f2;\n"
        + "}\n";

    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(block));

    assertEquals(4, outerStructDefinitionType.getNumFields());
    assertEquals(makeFieldname(0) + makeFieldname(0), outerStructDefinitionType.getFieldName(0));
    assertEquals(makeFieldname(0) + makeFieldname(1), outerStructDefinitionType.getFieldName(1));
    assertEquals(makeFieldname(0) + makeFieldname(2), outerStructDefinitionType.getFieldName(2));
    assertEquals(makeFieldname(1), outerStructDefinitionType.getFieldName(3));

  }

  private String makeStructName(int i) {
    return Constants.STRUCTIFICATION_STRUCT_PREFIX + i;
  }

  private String makeFieldname(int i) {
    return Constants.STRUCTIFICATION_FIELD_PREFIX + i;
  }

}
