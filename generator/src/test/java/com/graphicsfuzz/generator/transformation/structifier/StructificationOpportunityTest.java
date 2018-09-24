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

package com.graphicsfuzz.generator.transformation.structifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.StructDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.CannedRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class StructificationOpportunityTest {

  @Test
  public void applyStructification() {

    // A placeholder translation unit that has one (incomplete) declaration.
    // We want to check that the generated structs get put into this translation unit, before
    // the existing declaration.
    TranslationUnit tu = new TranslationUnit(
        null,
        Arrays.asList(new VariablesDeclaration(BasicType.FLOAT, new ArrayList<>())));

    // float v = 3.0;
    DeclarationStmt declStmt =
        new DeclarationStmt(
            new VariablesDeclaration(
                BasicType.FLOAT,
                new VariableDeclInfo(
                    "v",
                    null,
                    new ScalarInitializer(
                        new FloatConstantExpr("3.0")))));

    // float v = 3.0;
    // v = v + v;
    // {
    //   float v = 2.0;
    //   v = v + v;
    // }
    ExprStmt assignment = new ExprStmt(
        new BinaryExpr(
            new VariableIdentifierExpr("v"),
            new BinaryExpr(
                new VariableIdentifierExpr("v"),
                new VariableIdentifierExpr("v"),
                BinOp.ADD
            ),
            BinOp.ASSIGN
        )
    );
    BlockStmt block =
      new BlockStmt(
          Arrays.asList(
              declStmt.clone(),
              assignment.clone(),
              new BlockStmt(Arrays.asList(declStmt.clone(), assignment.clone()), true)
          ), true);

    CannedRandom generator = new CannedRandom(

        // To generate the struct

        2 /* (2 + 1) fields */,
        true /* first field is struct */,
        1 /* with (1 + 1) fields */,
        false /* first of which is not a struct */,
        0 /* instead it is FLOAT */,
        false /* second of which is not a struct */,
        1 /* instead it is VEC2 */,
        false /* second top-level field is not a struct */,
        0 /* instead it is FLOAT */,
        true /* third top-level field is a struct */,
        0 /* with (0 + 1) field */,
        false /* that is not a struct */,
        0 /* instead it is FLOAT */,

        // To insert variable into the struct
        true, /* choose to insert into struct field */
        0, /* choose to insert into the first of the struct fields */
        2 /* choose to insert at the end of this struct */
    );

    new StructificationOpportunity((DeclarationStmt) block.getStmt(0), block, tu,
        ShadingLanguageVersion.GLSL_440).apply(
        new IdGenerator(),
        generator,
        GenerationParams.normal(ShaderKind.FRAGMENT));
    // The generator should have used up all its values by now.
    assertTrue(generator.isExhausted());

    // Check that the right structs have been added
    assertEquals(4, tu.getTopLevelDeclarations().size()); // The dummy decl, plus 3 more structs

    // Check there are the same number of statements in the block
    assertEquals(3, block.getNumStmts());
    assertEquals(2, ((BlockStmt) block.getStmt(2)).getNumStmts());

    // Check that the inner declaration is still called v
    assertEquals("v",
        ((DeclarationStmt)((BlockStmt) block.getStmt(2)).getStmt(0)).getVariablesDeclaration()
           .getDeclInfo(0).getName());
    assertEquals(BasicType.FLOAT,
        ((DeclarationStmt)((BlockStmt) block.getStmt(2)).getStmt(0)).getVariablesDeclaration()
            .getBaseType());
    assertEquals("v",
        ((VariableIdentifierExpr)((BinaryExpr)((ExprStmt)((BlockStmt) block.getStmt(2)).getStmt(1)).getExpr())
            .getLhs()).getName());

    String newName = ((DeclarationStmt) block.getStmt(0)).getVariablesDeclaration().getDeclInfo(0).getName();
    assertTrue(newName.startsWith(Constants.GLF_STRUCT_REPLACEMENT));

    BinaryExpr newAssignment = (BinaryExpr) ((ExprStmt) block.getStmt(1)).getExpr();

    assertTrue(newAssignment.getLhs() instanceof MemberLookupExpr);
    assertEquals("v", ((MemberLookupExpr) newAssignment.getLhs()).getMember());
    assertTrue(((MemberLookupExpr) newAssignment.getLhs()).getStructure() instanceof MemberLookupExpr);
    assertEquals("_f0", ((MemberLookupExpr)((MemberLookupExpr) newAssignment.getLhs()).getStructure())
        .getMember());
    assertTrue(((MemberLookupExpr)((MemberLookupExpr) newAssignment.getLhs()).getStructure())
        .getStructure() instanceof VariableIdentifierExpr);
    assertEquals(newName,
        ((VariableIdentifierExpr)
        ((MemberLookupExpr)((MemberLookupExpr) newAssignment.getLhs()).getStructure())
            .getStructure()).getName()
        );

  }


  @Test
  public void createRandomStruct() {
    CannedRandom generator = new CannedRandom(
        2 /* (2 + 1) fields */,
        true /* first field is struct */,
        1 /* with (1 + 1) fields */,
        false /* first of which is not a struct */,
        0 /* instead it is FLOAT */,
        false /* second of which is not a struct */,
        1 /* instead it is VEC2 */,
        false /* second top-level field is not a struct */,
        0 /* instead it is FLOAT */,
        true /* third top-level field is a struct */,
        0 /* with (0 + 1) field */,
        false /* that is not a struct */,
        0 /* instead it is FLOAT */);
    List<StructDeclaration> structs = StructificationOpportunity.randomStruct(0, generator,
        new IdGenerator(), ShadingLanguageVersion.GLSL_440, GenerationParams.normal(ShaderKind.FRAGMENT));
    assertEquals(3, structs.size());
    StructType enclosingType = structs.get(0).getType();
    assertEquals(enclosingType.getFieldName(0), "_f0");
    assertEquals(enclosingType.getFieldName(1), "_f1");
    assertEquals(enclosingType.getFieldName(2), "_f2");
    assertTrue(enclosingType.getFieldType(0) instanceof StructType);
    assertEquals(enclosingType.getFieldType(0), structs.get(1).getType());
    assertEquals(enclosingType.getFieldName(1), "_f1");
    assertTrue(enclosingType.getFieldType(2) instanceof StructType);
    assertEquals(enclosingType.getFieldType(2), structs.get(2).getType());
    assertEquals(enclosingType.getFieldType(1), BasicType.FLOAT);
    assertEquals(((StructType) enclosingType.getFieldType(0)).getFieldType(0), BasicType.FLOAT);
    assertEquals(((StructType) enclosingType.getFieldType(0)).getFieldType(1), BasicType.VEC2);
    assertEquals(((StructType) enclosingType.getFieldType(2)).getFieldType(0), BasicType.FLOAT);
    assertTrue(generator.isExhausted());
  }

  @Test
  public void randomStruct() {
    for (int i = 0; i < 10; i++) {
      List<StructDeclaration> struct =
          StructificationOpportunity.randomStruct(0, new RandomWrapper(0),
              new IdGenerator(), ShadingLanguageVersion.ESSL_100,
              GenerationParams.normal(ShaderKind.FRAGMENT));
      checkDisjointSubStructs(struct.get(0).getType());
    }

  }

  private void checkDisjointSubStructs(StructType type) {
    Set<String> observedStructs = new HashSet<>();
    observedStructs.add(type.getName());
    for (Type t : type.getFieldTypes()) {
      if (t.getWithoutQualifiers() instanceof StructType) {
        checkDisjointSubStructs((StructType) t.getWithoutQualifiers());
        Set<String> referencedStructs = getAllReferencedStructs(
            (StructType) t.getWithoutQualifiers());
        for (String s : observedStructs) {
          assertFalse(referencedStructs.contains(s));
        }
        observedStructs.addAll(referencedStructs);
      }
    }
  }

  private Set<String> getAllReferencedStructs(StructType type) {
    Set<String> result = new HashSet<String>();
    result.add(type.getName());
    for (Type t : type.getFieldTypes()) {
      if (t.getWithoutQualifiers() instanceof StructType) {
        result.addAll(getAllReferencedStructs((StructType) t.getWithoutQualifiers()));
      }
    }
    return result;
  }


}