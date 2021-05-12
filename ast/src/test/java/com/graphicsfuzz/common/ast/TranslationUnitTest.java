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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ShaderKind;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TranslationUnitTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testShaderKinds() throws Exception {
    final File frag = temporaryFolder.newFile("a.frag");
    final File vert = temporaryFolder.newFile("a.vert");
    final File comp = temporaryFolder.newFile("a.comp");
    final String emptyShader = "#version 320 es\nvoid main() { }\n";
    FileUtils.writeStringToFile(frag, emptyShader, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(vert, emptyShader, StandardCharsets.UTF_8);
    FileUtils.writeStringToFile(comp, emptyShader, StandardCharsets.UTF_8);
    final TranslationUnit fragTu = ParseHelper.parse(frag);
    final TranslationUnit vertTu = ParseHelper.parse(vert);
    final TranslationUnit compTu = ParseHelper.parse(comp);
    assertEquals(ShaderKind.FRAGMENT, fragTu.getShaderKind());
    assertEquals(ShaderKind.VERTEX, vertTu.getShaderKind());
    assertEquals(ShaderKind.COMPUTE, compTu.getShaderKind());

  }

  @Test
  public void testClone() throws Exception {
    final File vert = temporaryFolder.newFile("a.vert");
    FileUtils.writeStringToFile(vert, "void main() { }", StandardCharsets.UTF_8);
    final TranslationUnit vertTu = ParseHelper.parse(vert);
    assertEquals(ShaderKind.VERTEX, vertTu.getShaderKind());
    assertEquals(ShaderKind.VERTEX, vertTu.clone().getShaderKind());
  }

  @Test
  public void testHasUniformDeclaration() throws Exception {
    final String shader =
        "uniform float a[2], b;"
            + "uniform int;"
            + "void main()"
            + "{"
            + "  float c = a[0];"
            + "  float d = b;"
            + "}";

    final TranslationUnit translationUnit = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    assertTrue(translationUnit.hasUniformDeclaration("a"));
    assertTrue(translationUnit.hasUniformDeclaration("b"));
    assertFalse(translationUnit.hasUniformDeclaration("c"));
  }

  @Test
  public void testUpdateTopLevelDeclaration() throws Exception {

    final String shader =
        "uniform float a[2];"
            + "void main()"
            + "{"
            + "  float b = a[0];"
            + "}";

    final TranslationUnit translationUnit = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    final VariablesDeclaration variablesDecl = translationUnit.getUniformDeclaration("a");

    assertEquals(variablesDecl.getDeclInfos().get(0).getName(), "a");
    assertTrue(variablesDecl.getDeclInfos().get(0).hasArrayInfo());
    assertEquals(variablesDecl.getDeclInfos().get(0).getArrayInfo().getConstantSize(0),
        Integer.valueOf(2));
    assertEquals(((IntConstantExpr)variablesDecl.getDeclInfos().get(0).getArrayInfo()
            .getSizeExpr(0)).getNumericValue(), 2);

    // Increases the size of the array by one and checks that the new size was recorded
    // correctly.
    final ArrayInfo arrayInfo =
        new ArrayInfo(Collections.singletonList(Optional.of(new IntConstantExpr("3"))));
    arrayInfo.setConstantSizeExpr(0, 3);
    final VariableDeclInfo variableDeclInfo = new VariableDeclInfo("a",
          arrayInfo, null);
    final VariablesDeclaration newVariablesDeclaration = new VariablesDeclaration(
        new QualifiedType(BasicType.INT, Arrays.asList(TypeQualifier.UNIFORM)), variableDeclInfo
    );

    translationUnit.updateTopLevelDeclaration(newVariablesDeclaration, variablesDecl);

    final VariablesDeclaration variablesDecl2 =
        translationUnit.getUniformDeclaration("a");

    assertEquals(variablesDecl2.getDeclInfos().get(0).getName(), "a");
    assertTrue(variablesDecl2.getDeclInfos().get(0).hasArrayInfo());
    assertEquals(variablesDecl2.getDeclInfos().get(0).getArrayInfo().getConstantSize(0),
        Integer.valueOf(3));
    assertEquals(((IntConstantExpr)variablesDecl2.getDeclInfos().get(0).getArrayInfo()
        .getSizeExpr(0)).getNumericValue(), 3);
  }

  @Test
  public void testGetUniformDeclaration() throws Exception {
    final String shader =
        "uniform float a, b;"
        + "uniform int;"
        + "void main()"
        + "{"
        + "  float c = a;"
        + "  float d = b;"
        + "}";

    final TranslationUnit translationUnit = ParseHelper.parse(shader, ShaderKind.FRAGMENT);
    final VariablesDeclaration variablesDeclaration =
        translationUnit.getUniformDeclaration("a");

    assertEquals(variablesDeclaration.getDeclInfos().get(0).getName(), "a");
    assertEquals(variablesDeclaration.getDeclInfos().get(1).getName(), "b");

    final VariablesDeclaration variablesDeclaration2 =
        translationUnit.getUniformDeclaration("b");

    assertEquals(variablesDeclaration2.getDeclInfos().get(0).getName(), "a");
    assertEquals(variablesDeclaration2.getDeclInfos().get(1).getName(), "b");
  }
}
