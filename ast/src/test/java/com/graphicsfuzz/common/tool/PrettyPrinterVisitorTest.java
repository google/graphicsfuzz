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

package com.graphicsfuzz.common.tool;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class PrettyPrinterVisitorTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testParseAndPrint() throws Exception {
    final String program = ""
        + "struct A {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int x;\n"
        + "} ;\n\n"
        + "struct B {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int y;\n"
        + "} ;\n\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintSwitch() throws Exception {
    final String program = ""
        + "void main()\n"
        + "{\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int x = 3;\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "switch(x)\n"
        + PrettyPrinterVisitor.defaultIndent(2) + "{\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "case 0:\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "x ++;\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "case 1:\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "case 3:\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "x ++;\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "break;\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "case 4:\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "break;\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "default:\n"
        + PrettyPrinterVisitor.defaultIndent(3) + "break;\n"
        + PrettyPrinterVisitor.defaultIndent(2) + "}\n"
        + "}\n";
    TranslationUnit tu = ParseHelper.parse(program);
    CompareAstsDuplicate.assertEqualAsts(tu, tu.clone());
  }

  @Test
  public void testStruct() throws Exception {
    final String program = "struct foo {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int data[10];\n"
        + "} ;\n\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test(expected = RuntimeException.class)
  public void testStruct2() throws Exception {
    // As we are not yet supporting 2D arrays, we expect a RuntimeException to be thrown.
    // When we do support 2D arrays this test should pass without exception.
    final String program = "struct foo {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int data[10][20];\n"
        + "};\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintQualifiers() throws Exception {
    // const volatile is not useful here, but this is just to test that qualifier order is preserved.
    final String program = ""
        + "const volatile float x;\n\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintLayout() throws Exception {
    // This test deliberately exposes that we presently do not dig into the inside of layout
    // qualifiers.  When we do, we will ditch this test and replace it with some tests that
    // check we are handling the internals properly.
    final String program = ""
        + "layout(location = 0) out vec4 color;\n\n"
        + "layout(anything=3, we, like=4, aswearenotyethandlingtheinternals) out vec2 blah;\n\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintComputeShader() throws Exception {
    final String program = ""
        + "layout(std430, binding = 2) buffer abuf {\n"
        + " int data[];\n"
        + "} ;\n"
        + "layout(local_size_x = 128, local_size_y = 1) in;\n"
        + "void main()\n"
        + "{\n"
        + " for(\n"
        + "     uint d = gl_WorkGroupSize.x / 2u;\n"
        + "     d > 0u;\n"
        + "     d >>= 1u\n"
        + " )\n"
        + "  {\n"
        + "   if(gl_LocalInvocationID.x < d)\n"
        + "    {\n"
        + "     data[gl_LocalInvocationID.x] += data[d + gl_LocalInvocationID.x];\n"
        + "    }\n"
        + "   barrier();\n"
        + "  }\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintStructs() throws Exception {
    // This checks exact layout, so will require maintenance if things change.
    // This is expected and deliberate: do the maintenance :)
    final String program = ""
        + "struct S {\n"
        + " int a;\n"
        + " int b;\n"
        + "} ;\n"
        + "\n"
        + "struct T {\n"
        + " S myS1;\n"
        + " S myS2;\n"
        + "} ;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " T myT = T(S(1, 2), S(3, 4));\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintStructs2() throws Exception {
    // This checks exact layout, so will require maintenance if things change.
    // This is expected and deliberate: do the maintenance :)
    final String program = ""
        + "struct S {\n"
        + " int a;\n"
        + " int b;\n"
        + "} ;\n"
        + "\n"
        + "S someS = S(8, 9);\n"
        + "\n"
        + "struct T {\n"
        + " S myS1;\n"
        + " S myS2;\n"
        + "} ;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " T myT = T(S(1, 2), S(3, 4));\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintStructs3() throws Exception {
    // This checks exact layout, so will require maintenance if things change.
    // This is expected and deliberate: do the maintenance :)
    final String program = ""
        + "struct S {\n"
        + " int a;\n"
        + " int b;\n"
        + "} someS = S(8, 9);\n"
        + "\n"
        + "struct T {\n"
        + " S myS1;\n"
        + " S myS2;\n"
        + "} ;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " T myT = T(S(1, 2), S(someS.a, 4));\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintStructs4() throws Exception {
    // This checks exact layout, so will require maintenance if things change.
    // This is expected and deliberate: do the maintenance :)
    final String program = ""
        + "struct {\n"
        + " int a;\n"
        + " int b;\n"
        + "} X, Y, Z;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " X.a = 3;\n"
        + " Y.b = X.a;\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintVersion() throws Exception {
    final String program = "#\tversion 100\nvoid main() { }\n";
    final String expected = "#version 100\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintVersionES() throws Exception {
    final String program = "#\tversion 310 es\nvoid main() { }\n";
    final String expected = "#version 310 es\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintExtension() throws Exception {
    final String program = ""
        + "#\textension GL_EXT_gpu_shader5 : enable\nvoid main() { }";
    final String expected = ""
        + "#extension GL_EXT_gpu_shader5 : enable\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintPragmaOptimizeOn() throws Exception {
    final String program = "#\tpragma optimize  ( on )\nvoid main() { }\n";
    final String expected = "#pragma optimize(on)\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintPragmaOptimizeOff() throws Exception {
    final String program = "#pragma optimize  ( off )\nvoid main() { }\n";
    final String expected = "#pragma optimize(off)\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintPragmaDebugOn() throws Exception {
    final String program = "#\tpragma debug  ( on )\nvoid main() { }\n";
    final String expected = "#pragma debug(on)\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintPragmaDebugOff() throws Exception {
    final String program = "#\tpragma debug  (   off )\nvoid main() { }\n";
    final String expected = "#pragma debug(off)\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintPragmaInvariantAll() throws Exception {
    final String program = "#\tpragma invariant  (   all )\nvoid main() { }\n";
    final String expected = "#pragma invariant(all)\nvoid main()\n{\n}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testSamplers() throws Exception {
    final String program =
        "uniform sampler1D s1;\n\n"
      + "uniform sampler2D s2;\n\n"
      + "uniform sampler2DRect s3;\n\n"
      + "uniform sampler3D s4;\n\n"
      + "uniform samplerCube s5;\n\n"
      + "uniform sampler1DShadow s7;\n\n"
      + "uniform sampler2DShadow s8;\n\n"
      + "uniform sampler2DRectShadow s9;\n\n"
      + "uniform samplerCubeShadow s10;\n\n"
      + "uniform sampler1DArray s11;\n\n"
      + "uniform sampler2DArray s12;\n\n"
      + "uniform sampler1DArrayShadow s13;\n\n"
      + "uniform sampler2DArrayShadow s14;\n\n"
      + "uniform samplerBuffer s15;\n\n"
      + "uniform samplerCubeArray s16;\n\n"
      + "uniform samplerCubeArrayShadow s17;\n\n"
      + "uniform isampler1D s18;\n\n"
      + "uniform isampler2D s19;\n\n"
      + "uniform isampler2DRect s20;\n\n"
      + "uniform isampler3D s21;\n\n"
      + "uniform isamplerCube s22;\n\n"
      + "uniform isampler1DArray s23;\n\n"
      + "uniform isampler2DArray s24;\n\n"
      + "uniform isamplerBuffer s25;\n\n"
      + "uniform isamplerCubeArray s26;\n\n"
      + "uniform usampler1D s27;\n\n"
      + "uniform usampler2D s28;\n\n"
      + "uniform usampler2DRect s29;\n\n"
      + "uniform usampler3D s30;\n\n"
      + "uniform usamplerCube s31;\n\n"
      + "uniform usampler1DArray s32;\n\n"
      + "uniform usampler2DArray s33;\n\n"
      + "uniform usamplerBuffer s34;\n\n"
      + "uniform usamplerCubeArray s35;\n\n"
      + "uniform sampler2DMS s36;\n\n"
      + "uniform isampler2DMS s37;\n\n"
      + "uniform usampler2DMS s38;\n\n"
      + "uniform sampler2DMSArray s39;\n\n"
      + "uniform isampler2DMSArray s40;\n\n"
      + "uniform usampler2DMSArray s41;\n\n";

    final File shaderFile = temporaryFolder.newFile("shader.frag");
    FileUtils.writeStringToFile(shaderFile, "#version 410\n\n" + program, StandardCharsets.UTF_8);
    assertEquals(0, ToolHelper.runValidatorOnShader(ExecHelper.RedirectType.TO_BUFFER,
        shaderFile).res);
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

}
