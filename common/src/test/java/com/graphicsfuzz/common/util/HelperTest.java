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

package com.graphicsfuzz.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HelperTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public static String TEST_PROGRAM = "void foo() { } void bar() { } void baz() { }";

  @Test
  public void testParseFromString() throws IOException, ParseTimeoutException {
    TranslationUnit tu = ParseHelper.parse(TEST_PROGRAM, false);
    checkTranslationUnit(tu);
  }

  @Test
  public void testParseFromFile() throws IOException, ParseTimeoutException {
    File tempFile = testFolder.newFile("shader.frag");
    BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
    bw.write(TEST_PROGRAM);
    bw.close();

    TranslationUnit tu = ParseHelper.parse(tempFile, false);
    checkTranslationUnit(tu);
  }

  @Test
  public void testParseFromFileWithHeader() throws IOException, ParseTimeoutException {
    File tempFile = testFolder.newFile("shader.frag");
    BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
    bw.write("arbitrary\nstuff\n" + ParseHelper.END_OF_HEADER + "\n" + TEST_PROGRAM);
    bw.close();

    TranslationUnit tu = ParseHelper.parse(tempFile, true);
    checkTranslationUnit(tu);
  }

  private void checkTranslationUnit(TranslationUnit tu) {
    assertEquals(3, tu.getTopLevelDeclarations().size());
    Set<String> names =
      tu.getTopLevelDeclarations()
          .stream()
          .filter(item -> item instanceof FunctionDefinition)
          .map(item -> ((FunctionDefinition)item).getPrototype().getName())
          .collect(Collectors.toSet());
    assertTrue(names.contains("foo"));
    assertTrue(names.contains("bar"));
    assertTrue(names.contains("baz"));
  }

  @Test
  public void testParseLegacyHeader() throws Exception {
    // Tests whether header of old receipient can be parsed; useful if we want to re-use old
    // shader sets.
    String program =
        "#version 100\n"
            + "\n"
            + "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "void main() { }\n";
    TranslationUnit tu = ParseHelper.parse(program, true);
    assertEquals(1, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testParseCurrentHeaderNoMacros() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    Helper.emitDefines(ps, ShadingLanguageVersion.WEBGL_SL,
        false);
    ps.println(TEST_PROGRAM);
    ps.close();
    TranslationUnit tu = ParseHelper.parse(tempFile, true);
    assertEquals(3, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testParseCurrentHeaderWithMacros() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    Helper.emitDefines(ps, ShadingLanguageVersion.WEBGL_SL,
        true);
    ps.println(TEST_PROGRAM);
    ps.close();
    TranslationUnit tu = ParseHelper.parse(tempFile, true);
    assertEquals(3, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testQualifiers() throws Exception {
    final String prog = "in vec3 blah; out vec3 bloo; void foo(in float x, out float y);";
    final TranslationUnit tu = ParseHelper.parse(prog, false);

    assertEquals(3, tu.getTopLevelDeclarations().size());
    {
      final Declaration decl = tu.getTopLevelDeclarations().get(0);
      final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
      assertTrue(variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHADER_INPUT));
      assertFalse(variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.IN_PARAM));
    }
    {
      final Declaration decl = tu.getTopLevelDeclarations().get(1);
      final VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
      assertTrue(variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHADER_OUTPUT));
      assertFalse(variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.OUT_PARAM));
    }
    {
      final Declaration decl = tu.getTopLevelDeclarations().get(2);
      FunctionPrototype proto = (FunctionPrototype) decl;
      assertTrue(proto.getParameters().get(0).getType().hasQualifier(TypeQualifier.IN_PARAM));
      assertFalse(proto.getParameters().get(0).getType().hasQualifier(TypeQualifier.SHADER_INPUT));
      assertTrue(proto.getParameters().get(1).getType().hasQualifier(TypeQualifier.OUT_PARAM));
      assertFalse(proto.getParameters().get(1).getType().hasQualifier(TypeQualifier.SHADER_OUTPUT));
    }
  }

}