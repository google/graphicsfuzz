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

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class ParseHelperTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  public static String TEST_PROGRAM = "void foo() { } void bar() { } void baz() { }";

  @Test
  public void testParseFromString() throws Exception {
    TranslationUnit tu = ParseHelper.parse(TEST_PROGRAM);
    checkTranslationUnit(tu);
  }

  @Test
  public void testParseFromFile() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
    bw.write(TEST_PROGRAM);
    bw.close();

    TranslationUnit tu = ParseHelper.parse(tempFile);
    checkTranslationUnit(tu);
  }

  @Test
  public void testParseFromFileWithHeader() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));
    bw.write("arbitrary\nstuff\n" + ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES + "\n" + TEST_PROGRAM);
    bw.close();

    TranslationUnit tu = ParseHelper.parse(tempFile);
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
  public void testParseCurrentHeaderNoMacros() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    PrettyPrinterVisitor.emitGraphicsFuzzDefines(ps);
    ps.println(TEST_PROGRAM);
    ps.close();
    TranslationUnit tu = ParseHelper.parse(tempFile);
    assertEquals(3, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testParseCurrentHeaderWithMacros() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    PrettyPrinterVisitor.emitGraphicsFuzzDefines(ps);
    ps.println(TEST_PROGRAM);
    ps.close();
    TranslationUnit tu = ParseHelper.parse(tempFile);
    assertEquals(3, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testQualifiers() throws Exception {
    final String prog = "in vec3 blah; out vec3 bloo; void foo(in float x, out float y);";
    final TranslationUnit tu = ParseHelper.parse(prog);

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

  @Test
  public void testStripHeader() throws Exception {
    final String withHeader =
          "#version 310 es\n"
        + "//WebGL\n"
        + "//Some\n"
        + "//Stuff\n"
        + "// END OF GENERATED HEADER\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    final String expected =
        "#version 310 es\n"
            + "//WebGL\n"
            + "void main()\n"
            + "{\n"
            + "}\n";
    final InputStream is = new ByteArrayInputStream(withHeader.getBytes(StandardCharsets.UTF_8));
    final InputStream strippedIs = ParseHelper.stripGraphicsFuzzDefines(is);
    assertNotSame(is, strippedIs);
    assertEquals(expected, getStringFromInputStream(strippedIs));
  }

  @Test
  public void testStripHeader2() throws Exception {
    final String withHeader =
        "#version 440\n"
            + "//Some\n"
            + "//Stuff\n"
            + "\n\n\n"
            + "// END OF GENERATED HEADER\n"
            + "void main()\n"
            + "{\n"
            + "}\n";
    final String expected =
        "#version 440\n"
            + "void main()\n"
            + "{\n"
            + "}\n";
    final InputStream is = new ByteArrayInputStream(withHeader.getBytes(StandardCharsets.UTF_8));
    final InputStream strippedIs = ParseHelper.stripGraphicsFuzzDefines(is);
    assertNotSame(is, strippedIs);
    assertEquals(expected, getStringFromInputStream(strippedIs));
  }

  @Test
  public void testStripHeader3() throws Exception {
    final String withHeader =
        "//Some\n"
            + "//Stuff\n"
            + "\n\n\n"
            + "// END OF GENERATED HEADER\n"
            + "void main()\n"
            + "{\n"
            + "}\n";
    final String expected =
        "void main()\n"
            + "{\n"
            + "}\n";
    final InputStream is = new ByteArrayInputStream(withHeader.getBytes(StandardCharsets.UTF_8));
    final InputStream strippedIs = ParseHelper.stripGraphicsFuzzDefines(is);
    assertNotSame(is, strippedIs);
    assertEquals(expected, getStringFromInputStream(strippedIs));
  }


  @Test
  public void testDoNotStripHeader() throws Exception {
    final String withoutHeader =
        "#version 310 es\n"
            + "//WebGL\n"
            + "//Some\n"
            + "//Stuff\n"
            + "void main()\n"
            + "{\n"
            + "}\n";
    final InputStream is = new ByteArrayInputStream(withoutHeader.getBytes(StandardCharsets.UTF_8));
    final InputStream strippedIs = ParseHelper.stripGraphicsFuzzDefines(is);
    assertSame(is, strippedIs);
  }

  @Test
  public void testHashDefinePreprocessing() throws Exception {
    final String original = "#version 310 es\n"
        + "#define N 100\n"
        + "#define NAME main\n"
        + "#define OPEN (\n"
        + "#define CLOSE )\n"
        + "#define VOID void\n"
        + "VOID NAME OPEN CLOSE {\n"
        + "  N;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testIfdefPreprocessing() throws Exception {
    final String original = "#version 310 es\n"
        + "#define N 100\n"
        + "#ifdef N\n"
        + "#define NAME main\n"
        + "#else\n"
        + "some nonsense\n"
        + "#endif\n"
        + "#define OPEN (\n"
        + "#define CLOSE )\n"
        + "#define VOID void\n"
        + "VOID NAME OPEN CLOSE {\n"
        + "#ifdef NAME\n"
        + "  N;\n"
        + "#endif\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testIfdefPreprocessing2() throws Exception {
    final String original = "#version 310 es\n"
        + "#define N 100\n"
        + "#ifdef M\n"
        + "some nonsense\n"
        + "#else\n"
        + "#define NAME main\n"
        + "#endif\n"
        + "#define OPEN (\n"
        + "#define CLOSE )\n"
        + "#define VOID void\n"
        + "VOID NAME OPEN CLOSE {\n"
        + "#ifdef Z\n"
        + "#else\n"
        + "  N;\n"
        + "#endif\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testIfndefPreprocessing() throws Exception {
    final String original = "#version 310 es\n"
        + "#define N 100\n"
        + "#ifndef N\n"
        + "some nonsense\n"
        + "#else\n"
        + "#define NAME main\n"
        + "#endif\n"
        + "#define OPEN (\n"
        + "#define CLOSE )\n"
        + "#define VOID void\n"
        + "VOID NAME OPEN CLOSE {\n"
        + "#ifndef NAME\n"
        + "#else\n"
        + "  N;\n"
        + "#endif\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testIfndefPreprocessing2() throws Exception {
    final String original = "#version 310 es\n"
        + "#define N 100\n"
        + "#ifndef M\n"
        + "#define NAME main\n"
        + "#else\n"
        + "some nonsense\n"
        + "#endif\n"
        + "#define OPEN (\n"
        + "#define CLOSE )\n"
        + "#define VOID void\n"
        + "VOID NAME OPEN CLOSE {\n"
        + "#ifndef Z\n"
        + "  N;\n"
        + "#else\n"
        + "#endif\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main() {\n"
        + "  100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testStripHeaderAndPreprocess() throws Exception {

    // Checks that special GraphicsFuzz headers are stripped *before* preprocessing.

    final String original = "#version 310 es\n"
        + "//Some stuff\n"
        + "#define leave_me_alone ZZZZ\n"
        + "\n\n\n"
        + "// END OF GENERATED HEADER\n"
        + "#define N 100\n"
        + "void main()\n"
        + "{\n"
        + "  int leave_me_alone = N;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "void main()\n"
        + "{\n"
        + "  int leave_me_alone = 100;\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(original, expected);
  }

  @Test
  public void testParseOctalIntLiteral() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() {"
        + "  int x = 031;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    assertTrue(
      new CheckPredicateVisitor() {
        @Override
        public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
          super.visitScalarInitializer(scalarInitializer);
          if (scalarInitializer.getExpr() instanceof IntConstantExpr
            && ((IntConstantExpr) scalarInitializer.getExpr()).getValue().equals("031")) {
            predicateHolds();
          }
        }
      }.test(tu));
  }

  @Test
  public void testParseHexIntLiteral() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() {"
        + "  int x = 0xa03b;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    assertTrue(
        new CheckPredicateVisitor() {
          @Override
          public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
            super.visitScalarInitializer(scalarInitializer);
            if (scalarInitializer.getExpr() instanceof IntConstantExpr
                && ((IntConstantExpr) scalarInitializer.getExpr()).getValue().equals("0xa03b")) {
              predicateHolds();
            }
          }
        }.test(tu));
  }

  @Test
  public void testParseOctalUnsignedIntLiteral() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() {"
        + "  int x = 031u;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    assertTrue(
        new CheckPredicateVisitor() {
          @Override
          public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
            super.visitScalarInitializer(scalarInitializer);
            if (scalarInitializer.getExpr() instanceof UIntConstantExpr
                && ((UIntConstantExpr) scalarInitializer.getExpr()).getValue().equals("031u")) {
              predicateHolds();
            }
          }
        }.test(tu));
  }

  @Test
  public void testParseHexUnsignedIntLiteral() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() {"
        + "  int x = 0xA03Bu;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    assertTrue(
        new CheckPredicateVisitor() {
          @Override
          public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
            super.visitScalarInitializer(scalarInitializer);
            if (scalarInitializer.getExpr() instanceof UIntConstantExpr
                && ((UIntConstantExpr) scalarInitializer.getExpr()).getValue().equals("0xA03Bu")) {
              predicateHolds();
            }
          }
        }.test(tu));
  }

  @Test
  public void testParseUnsignedZero() throws Exception {
    final String program = "uint foo() { return 0u; }";
    assertTrue(
        new CheckPredicateVisitor() {
          @Override
          public void visitReturnStmt(ReturnStmt returnStmt) {
            super.visitReturnStmt(returnStmt);
            if (returnStmt.getExpr() instanceof UIntConstantExpr &&
                ((UIntConstantExpr) returnStmt.getExpr()).getValue().equals("0u")) {
              predicateHolds();
            }
          }
        }.test(ParseHelper.parse(program)));
  }

  @Test(expected = GlslParserException.class)
  public void testDoNotParseBadSignedConstant() throws Exception {
    // Check that lexing of hex constants is working OK.
    ParseHelper.parse("int foo() { return 0x120x12; }");
  }

  @Test(expected = GlslParserException.class)
  public void testDoNotParseBadUnsignedConstant() throws Exception {
    // Check that lexing of hex constants is working OK.
    ParseHelper.parse("uint foo() { return 0x120x12u; }");
  }

  @Test
  public void testParseEssl320() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 320 es\n"
        + "void main() { }");
    assertSame(ShadingLanguageVersion.ESSL_320, tu.getShadingLanguageVersion());
  }

  @Test
  public void testParseInPlacePrecisionAndFloatSuffix() throws Exception {
    final String shader = "#version 310 es\n"
        + "\n"
        + "void main() {\n"
        + "    mediump vec3 color = 1.00f;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    final VariablesDeclaration variablesDeclaration =
        ((DeclarationStmt) ((FunctionDefinition) tu.getTopLevelDeclarations().get(0)).getBody()
        .getStmt(0)).getVariablesDeclaration();
    final Type baseType =
        variablesDeclaration.getBaseType();
    assertTrue(baseType.hasQualifier(TypeQualifier.MEDIUMP));
    assertEquals("1.00f",
        ((FloatConstantExpr) ((ScalarInitializer) variablesDeclaration.getDeclInfo(0)
            .getInitializer()).getExpr()).getValue());
  }

  private String getStringFromInputStream(InputStream strippedIs) throws IOException {
    StringWriter writer = new StringWriter();
    IOUtils.copy(strippedIs, writer, StandardCharsets.UTF_8);
    return writer.toString();
  }

}
