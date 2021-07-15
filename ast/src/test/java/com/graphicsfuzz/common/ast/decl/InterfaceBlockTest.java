/*
 * Copyright 2021 The GraphicsFuzz Project Authors
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

package com.graphicsfuzz.common.ast.decl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.util.ParseHelper;
import org.junit.Test;

public class InterfaceBlockTest {

  @Test
  public void getInterfaceQualifiers() throws Exception {
    final String shader = "#version 320 es\n"
        + "buffer coherent readonly restrict someblock {\n"
        + "  int a;\n"
        + "};\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final InterfaceBlock interfaceBlock = (InterfaceBlock) tu.getTopLevelDeclarations().get(0);
    assertEquals(4, interfaceBlock.getInterfaceQualifiers().size());
    assertTrue(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.BUFFER));
    assertTrue(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.COHERENT));
    assertTrue(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.READONLY));
    assertTrue(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.RESTRICT));
    assertFalse(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.UNIFORM));
    assertFalse(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.SHADER_INPUT));
    assertFalse(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.SHADER_OUTPUT));
    assertFalse(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.VOLATILE));
    assertFalse(interfaceBlock.getInterfaceQualifiers().contains(TypeQualifier.WRITEONLY));
  }

  @Test
  public void isUniformBlock() throws Exception {
    final String shader = "#version 320 es\n"
        + "uniform someblock {\n"
        + "  int a;\n"
        + "};\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final InterfaceBlock interfaceBlock = (InterfaceBlock) tu.getTopLevelDeclarations().get(0);
    assertTrue(interfaceBlock.isUniformBlock());
    assertEquals(1, interfaceBlock.getInterfaceQualifiers().size());
  }

  @Test
  public void isShaderStorageBlock() throws Exception {
    final String shader = "#version 320 es\n"
        + "buffer someblock {\n"
        + "  int a;\n"
        + "};\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final InterfaceBlock interfaceBlock = (InterfaceBlock) tu.getTopLevelDeclarations().get(0);
    assertTrue(interfaceBlock.isShaderStorageBlock());
    assertEquals(1, interfaceBlock.getInterfaceQualifiers().size());
  }

  @Test
  public void isInputBlock() throws Exception {
    final String shader = "#version 320 es\n"
        + "in someblock {\n"
        + "  int a;\n"
        + "};\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final InterfaceBlock interfaceBlock = (InterfaceBlock) tu.getTopLevelDeclarations().get(0);
    assertTrue(interfaceBlock.isInputBlock());
    assertEquals(1, interfaceBlock.getInterfaceQualifiers().size());
  }

  @Test
  public void isOutputBlock() throws Exception {
    final String shader = "#version 320 es\n"
        + "out someblock {\n"
        + "  int a;\n"
        + "};\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final InterfaceBlock interfaceBlock = (InterfaceBlock) tu.getTopLevelDeclarations().get(0);
    assertTrue(interfaceBlock.isOutputBlock());
    assertEquals(1, interfaceBlock.getInterfaceQualifiers().size());
  }
}
