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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.UnsupportedLanguageFeatureException;
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
    PrettyPrinterVisitor.emitGraphicsFuzzDefines(ps, ShadingLanguageVersion.ESSL_320);
    ps.println(TEST_PROGRAM);
    ps.close();
    TranslationUnit tu = ParseHelper.parse(tempFile);
    assertEquals(3, tu.getTopLevelDeclarations().size());
  }

  @Test
  public void testParseCurrentHeaderWithMacros() throws Exception {
    File tempFile = testFolder.newFile("shader.frag");
    PrintStream ps = new PrintStream(new FileOutputStream(tempFile));
    PrettyPrinterVisitor.emitGraphicsFuzzDefines(ps, ShadingLanguageVersion.ESSL_310);
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
    assertTrue(new CheckPredicateVisitor() {
        @Override
        public void visitInitializer(Initializer initializer) {
          super.visitInitializer(initializer);
          if (initializer.getExpr() instanceof IntConstantExpr
              && ((IntConstantExpr) initializer.getExpr()).getValue().equals("031")) {
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
          public void visitInitializer(Initializer initializer) {
            super.visitInitializer(initializer);
            if (initializer.getExpr() instanceof IntConstantExpr
                && ((IntConstantExpr) initializer.getExpr()).getValue().equals("0xa03b")) {
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
          public void visitInitializer(Initializer initializer) {
            super.visitInitializer(initializer);
            if (initializer.getExpr() instanceof UIntConstantExpr
                && ((UIntConstantExpr) initializer.getExpr()).getValue().equals("031u")) {
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
          public void visitInitializer(Initializer initializer) {
            super.visitInitializer(initializer);
            if (initializer.getExpr() instanceof UIntConstantExpr
                && ((UIntConstantExpr) initializer.getExpr()).getValue().equals("0xA03Bu")) {
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
            if (returnStmt.getExpr() instanceof UIntConstantExpr
                && ((UIntConstantExpr) returnStmt.getExpr()).getValue().equals("0u")) {
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
  public void testParseWebGl1() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 100\n"
        + "//WebGL\n"
        + "void main() { }");
    assertSame(ShadingLanguageVersion.WEBGL_SL, tu.getShadingLanguageVersion());
  }

  @Test
  public void testParseWebGl2() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "//WebGL\n"
        + "void main() { }");
    assertSame(ShadingLanguageVersion.WEBGL2_SL, tu.getShadingLanguageVersion());
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
        ((FloatConstantExpr) variablesDeclaration.getDeclInfo(0)
            .getInitializer().getExpr()).getValue());
  }

  @Test
  public void testParseSharedKeyword() throws Exception {
    final String program = "#version 310 es\n"
        + "layout(std430, binding = 0) buffer theSSBO {\n"
        + "  bool b;\n"
        + "};\n"
        + "\n"
        + "layout(local_size_x=20, local_size_y=1, local_size_z=1) in;\n"
        + "\n"
        + "shared float result[10];\n"
        + "\n"
        + "void main() { }\n";
    final TranslationUnit tu = ParseHelper.parse(program, ShaderKind.COMPUTE);
    assertTrue(new CheckPredicateVisitor() {
        @Override
        public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
          super.visitVariablesDeclaration(variablesDeclaration);
          if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHARED)) {
            predicateHolds();
          }
        }
      }.test(tu));


  }

  @Test
  public void testParseError() throws Exception {
    try {
      ParseHelper.parse("void foo(");
      fail("Expected GlslParserException to be thrown.");
    } catch (GlslParserException exception) {
      // nothing
    }
  }

  private String getStringFromInputStream(InputStream strippedIs) throws IOException {
    StringWriter writer = new StringWriter();
    IOUtils.copy(strippedIs, writer, StandardCharsets.UTF_8);
    return writer.toString();
  }

  /**
   * Validate folding in testSupportedArrayLength. We know that the array is the last statement
   * in the function, but it may not be the first.
   * @param shaderSource Source code for the shader to test
   * @param expectedSize Expected size of the array
   * @throws Exception Producing the AST may throw exceptions
   */
  private void validateFolding(String shaderSource, int expectedSize) throws Exception {
    final BlockStmt block = ParseHelper.parse(shaderSource)
        .getMainFunction().getBody();
    assertEquals(expectedSize,
        (int) ((DeclarationStmt) block.getStmt(block.getNumStmts() - 1))
            .getVariablesDeclaration().getDeclInfo(0).getArrayInfo().getConstantSize(0));
  }

  /**
   * Tests various forms of statements that may occur inside array size declaration.
   * @throws Exception Producing the AST may throw exceptions
   */
  @Test
  public void testSupportedArrayLength() throws Exception {
    try {
      validateFolding("void main() {\n"
          + "  int A[3 + 4];\n"
          + "}\n", 7);
      validateFolding("void main() {\n"
          + "  int A[4 - 3];\n"
          + "}\n", 1);
      validateFolding("void main() {\n"
          + "  int A[3 * 4];\n"
          + "}\n", 12);
      validateFolding("void main() {\n"
          + "  int A[4 / 2];\n"
          + "}\n", 2);
      validateFolding("void main() {\n"
          + "  int A[3 << 2];\n"
          + "}\n", 12);
      validateFolding("void main() {\n"
          + "  int A[8 >> 2];\n"
          + "}\n", 2);
      validateFolding("void main() {\n"
          + "  int A[(3 + 4)];\n"
          + "}\n", 7);
      validateFolding("void main() {\n"
          + "  int A[3 && 4];\n"
          + "}\n", 1);
      validateFolding("void main() {\n"
          + "  int A[3 & 5];\n"
          + "}\n", 1);
      validateFolding("void main() {\n"
          + "  int A[3 || 4];\n"
          + "}\n", 1);
      validateFolding("void main() {\n"
          + "  int A[3 | 4];\n"
          + "}\n", 7);
      validateFolding("void main() {\n"
          + "  int A[3 ^^ 4];\n"
          + "}\n", 0);
      validateFolding("void main() {\n"
          + "  int A[3 ^ 4];\n"
          + "}\n", 7);
      validateFolding("void main() {\n"
          + "  int A[3 + 4 + 5];\n"
          + "}\n", 12);
      validateFolding("void main() {\n"
          + "  const int v = 5;\n"
          + "  int A[3 + v];\n"
          + "}\n", 8);
      validateFolding("void main() {\n"
          + "  const int v = 5;\n"
          + "  int A[(((3 + 4) * v) >> 2) && 7];\n"
          + "}\n", 1);
    } catch (UnsupportedLanguageFeatureException exception) {
      fail(exception.getMessage());
    }
  }

  @Test
  public void testParsingComma1() throws Exception {
    final TranslationUnit tu = ParseHelper.parse(""
        + "void main() {\n"
        + "  bool b, c;\n"
        + "  int x, y;\n"
        + "  b, c ? x : y;\n"
        + "  b ? c, x : y;\n"
        + "  b ? x : y, c;\n"
        + "}\n");
    final BlockStmt block = tu.getMainFunction().getBody();
    assertTrue(block.getStmt(0) instanceof DeclarationStmt);
    assertTrue(block.getStmt(1) instanceof DeclarationStmt);

    {
      final ExprStmt exprStmt = (ExprStmt) block.getStmt(2);
      assertTrue(exprStmt.getExpr() instanceof BinaryExpr);
      final BinaryExpr binaryExpr = (BinaryExpr) exprStmt.getExpr();
      assertEquals(BinOp.COMMA, binaryExpr.getOp());
      assertTrue(binaryExpr.getLhs() instanceof VariableIdentifierExpr);
      assertTrue(binaryExpr.getRhs() instanceof TernaryExpr);
    }

    {
      final ExprStmt exprStmt = (ExprStmt) block.getStmt(3);
      assertTrue(exprStmt.getExpr() instanceof TernaryExpr);
      final TernaryExpr ternaryExpr = (TernaryExpr) exprStmt.getExpr();
      assertTrue(ternaryExpr.getTest() instanceof VariableIdentifierExpr);
      assertTrue(ternaryExpr.getThenExpr() instanceof BinaryExpr);
      assertEquals(BinOp.COMMA, ((BinaryExpr) ternaryExpr.getThenExpr()).getOp());
      assertTrue(ternaryExpr.getElseExpr() instanceof VariableIdentifierExpr);
    }

    {
      final ExprStmt exprStmt = (ExprStmt) block.getStmt(4);
      assertTrue(exprStmt.getExpr() instanceof BinaryExpr);
      final BinaryExpr binaryExpr = (BinaryExpr) exprStmt.getExpr();
      assertEquals(BinOp.COMMA, binaryExpr.getOp());
      assertTrue(binaryExpr.getLhs() instanceof TernaryExpr);
      assertTrue(binaryExpr.getRhs() instanceof VariableIdentifierExpr);
    }

  }

  @Test
  public void testMultiDimensionalArraysSupported() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("void main() {\n"
        + "  int A[3][4];\n"
        + "}\n");
    assertTrue(new CheckPredicateVisitor() {
      @Override
      public void visitArrayInfo(ArrayInfo arrayInfo) {
        if (arrayInfo.getDimensionality() == 2 && arrayInfo.getConstantSize(0) == 3
            && arrayInfo.getConstantSize(1) == 4) {
          predicateHolds();
        }
      }
    }.test(tu));
  }

  @Test
  public void testUnsupportedArrayInBaseType() throws Exception {

    // Change this test to check for support if it is eventually introduced.

    try {
      ParseHelper.parse("void main() {\n"
          + "  int[2] A, B[3];\n"
          + "  B[2][1] = 3;\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("Array information specified at the base type"));
    }
  }

  @Test
  public void testUnsupportedDeclarationInCondition() throws Exception {

    // Change this test to check for support if it is eventually introduced.

    try {
      ParseHelper.parse("void main() {\n"
          + "  while(bool b = true) {\n"
          + "    if(b) {\n"
          + "      break;\n"
          + "    }\n"
          + "  }\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("We do not yet support the case where the "
          + "condition of a 'for' or 'while' introduces a new variable"));
    }
  }

  @Test
  public void testUnsupportedInitializerList() throws Exception {

    // Change this test to check for support if it is eventually introduced.

    try {
      ParseHelper.parse("#version 440\n"
          + "\n"
          + "void main() {\n"
          + "\n"
          + "  int A[4] = { 1, 2, 3, 4 };\n"
          + "\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("Initializer lists are not currently supported"));
    }
  }

  @Test
  public void testUnsupportedMethodCall1() throws Exception {

    // GLSL has method call syntax, but this is only used for the length() method.

    try {
      ParseHelper.parse("#version 310 es\n"
          + "\n"
          + "void main() {\n"
          + "\n"
          + "  int A[4];\n"
          + "  A.foo(1);\n"
          + "\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("Method calls with parameters are allowed by the "
          + "GLSL grammar but have no meaning in the language at present"));
    }
  }

  @Test
  public void testUnsupportedMethodCall2() throws Exception {

    // GLSL has method call syntax, but this is only used for the length() method.

    try {
      ParseHelper.parse("#version 310 es\n"
          + "\n"
          + "void main() {\n"
          + "\n"
          + "  int A[4];\n"
          + "  A.foo();\n"
          + "\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("The only allowed method call in GLSL is to "
          + "length()"));
    }
  }

  @Test
  public void testUnsupportedNamedInterfaceBlock() throws Exception {

    // Change this test to check for support if it is eventually introduced.

    try {
      ParseHelper.parse("#version 320 es\n"
          + "\n"
          + "layout(std430, binding = 0) buffer doesNotMatter {\n"
          + "  int x;\n"
          + "  int data[];\n"
          + "} block_name_not_currently_supported;\n"
          + "void main() {\nprecision highp float;\n"
          + "}\n");
      fail("Exception was expected");
    } catch (UnsupportedLanguageFeatureException exception) {
      assertTrue(exception.getMessage().contains("Named interface blocks are not currently "
          + "supported"));
    }
  }

}
