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
import org.junit.Test;

public class PrettyPrinterVisitorTest {

  @Test
  public void testParseAndPrint() throws Exception {
    final String program = ""
        + "struct A {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int x;\n"
        + "};\n"
        + "struct B {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int y;\n"
        + "};\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
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
    TranslationUnit tu = ParseHelper.parse(program, false);
    CompareAstsDuplicate.assertEqualAsts(tu, tu.clone());
  }

  @Test
  public void testStruct() throws Exception {
    final String program = "struct foo {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int data[10];\n"
        + "};\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
  }

  @Test(expected = RuntimeException.class)
  public void testStruct2() throws Exception {
    // As we are not yet supporting 2D arrays, we expect a RuntimeException to be thrown.
    // When we do support 2D arrays this test should pass without exception.
    final String program = "struct foo {\n"
        + PrettyPrinterVisitor.defaultIndent(1) + "int data[10][20];\n"
        + "};\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
  }

  @Test
  public void testParseAndPrintQualifiers() throws Exception {
    // const volatile is not useful here, but this is just to test that qualifier order is preserved.
    final String program = ""
        + "const volatile float x;\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
  }

  @Test
  public void testParseAndPrintLayout() throws Exception {
    final String program = ""
        + "layout(location = 0) out vec4 color;\n"
        + "layout(anything = 3, we, like = 4, aswearenotyethandlingtheinternals) out vec2 blah;\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
  }

  @Test
  public void testParseAndPrintComputeShader() throws Exception {
    final String program = ""
        + "layout(std430, binding = 2) buffer abuf { int data[]; };\n"
        + "\n"
        + "layout(local_size_x=128, local_size_y=1) in;\n"
        + "\n"
        + "void main() {\n"
        + "  for (uint d = gl_WorkGroupSize.x / 2u; d > 0u; d >>= 1u) {\n"
        + "    if (gl_LocalInvocationID.x < d) {\n"
        + "       data[gl_LocalInvocationID.x] += data[d + gl_LocalInvocationID.x];\n"
        + "    }\n"
        + "    barrier();\n"
        + "  }\n"
        + "}\n";
    CompareAstsDuplicate.assertEqualAsts(program, ParseHelper.parse(program, false));
  }

}