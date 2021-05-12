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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.CompareAstsDuplicate;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.Constants;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ToolHelper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrettyPrinterVisitorTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testArraySizeExpression() throws Exception {
    final String program = ""
        + "void main()\n"
        + "{\n"
        + " int a[3 + 4];\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testLiteralUniformDefines() throws Exception {
    final String shaderWithBindings = ""
        + "layout(set = 0, binding = 0) uniform buf0 { int _GLF_uniform_int_values[2]; };"
        + "layout(set = 0, binding = 1) uniform buf1 { uint _GLF_uniform_uint_values[1]; };"
        + "layout(set = 0, binding = 2) uniform buf2 { float _GLF_uniform_float_values[3]; };"
        + "layout(set = 0, binding = 3) uniform sampler2D tex;"
        + "void main() { "
        + "int a = _GLF_uniform_int_values[0];"
        + "int b = _GLF_uniform_int_values[1];"
        + "uint c = _GLF_uniform_uint_values[0];"
        + "float d = _GLF_uniform_float_values[0];"
        + "float e = _GLF_uniform_float_values[1];"
        + "float f = _GLF_uniform_float_values[2];"
        + "}";

    final String shaderPrettyPrinted = ""
        + "#define _int_0 _GLF_uniform_int_values[0]\n"
        + "#define _int_2 _GLF_uniform_int_values[1]\n"
        + "#define _uint_72 _GLF_uniform_uint_values[0]\n"
        + "#define _float_0_0 _GLF_uniform_float_values[0]\n"
        + "#define _float_22_4 _GLF_uniform_float_values[1]\n"
        + "#define _float_11_3 _GLF_uniform_float_values[2]\n"
        + "\n"
        + "// Contents of _GLF_uniform_int_values: [0, 2]\n"
        + "layout(set = 0, binding = 0) uniform buf0 {\n"
        + " int _GLF_uniform_int_values[2];\n"
        + "};\n"

        + "// Contents of _GLF_uniform_uint_values: 72\n"
        + "layout(set = 0, binding = 1) uniform buf1 {\n"
        + " uint _GLF_uniform_uint_values[1];\n"
        + "};\n"

        + "// Contents of _GLF_uniform_float_values: [0.0, 22.4, 11.3]\n"
        + "layout(set = 0, binding = 2) uniform buf2 {\n"
        + " float _GLF_uniform_float_values[3];\n"
        + "};\n"

        + "layout(set = 0, binding = 3) uniform sampler2D tex;\n\n"

        + "void main()\n"
        + "{\n"

        + " int a = _int_0;\n"
        + " int b = _int_2;\n"
        + " uint c = _uint_72;\n"
        + " float d = _float_0_0;\n"
        + " float e = _float_22_4;\n"
        + " float f = _float_11_3;\n"
        + "}\n";

    UniformValueSupplier uniformValues = name -> {
      if (name.equals("_GLF_uniform_int_values")) {
        return Optional.of(Arrays.asList("0", "2"));
      } else if (name.equals("_GLF_uniform_float_values")) {
        return Optional.of(Arrays.asList("0.0", "22.4", "11.3"));
      } else if (name.equals("_GLF_uniform_uint_values")) {
        return Optional.of(Arrays.asList("72"));
      } else if (name.equals("tex")) {
        throw new RuntimeException("The value of a sampler uniform should not be requested.");
      }
      return Optional.empty();
    };

    assertEquals(shaderPrettyPrinted,
        PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shaderWithBindings),
            uniformValues));
  }

  @Test
  public void testUniformBlockContentsInComments() throws Exception {
    final String shaderWithBindings = ""
        + "layout(set = 0, binding = 0) uniform buf0 { int _GLF_uniform_int_values[2]; };"
        + "layout(set = 0, binding = 1) uniform buf1 { float _GLF_uniform_float_values[1]; };"
        + "void main() { }";

    final String shaderPrettyPrinted = ""
        + "#define _int_0 _GLF_uniform_int_values[0]\n"
        + "#define _int_1 _GLF_uniform_int_values[1]\n"
        + "#define _float_3_0 _GLF_uniform_float_values[0]\n"
        + "\n"
        + "// Contents of _GLF_uniform_int_values: [0, 1]\n"
        + "layout(set = 0, binding = 0) uniform buf0 {\n"
        + " int _GLF_uniform_int_values[2];\n"
        + "};\n"
        + "// Contents of _GLF_uniform_float_values: 3.0\n"
        + "layout(set = 0, binding = 1) uniform buf1 {\n"
        + " float _GLF_uniform_float_values[1];\n"
        + "};\n"
        + "void main()\n"
        + "{\n"
        + "}\n";

    UniformValueSupplier uniformValues = name -> {
      if (name.equals("_GLF_uniform_int_values")) {
        return Optional.of(Arrays.asList("0", "1"));
      } else if (name.equals("_GLF_uniform_float_values")) {
        return Optional.of(Arrays.asList("3.0"));
      }
      return Optional.empty();
    };

    assertEquals(shaderPrettyPrinted,
        PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shaderWithBindings),
            uniformValues));
  }

  @Test
  public void testUniformArrayContentsInComments() throws Exception {

    final String shader = ""
        + "uniform int a[3], b[2], X[1], Y[22], Z[1];"
        + "uniform float _GLF_uniform_float_values[3], D;"
        + "uniform uint _GLF_uniform_uint_values[2], E[33];"
        + "uniform int test[5], test2[1], A;"
        + "void main()"
        + "{"
        + "  float a = _GLF_uniform_float_values[0];"
        + "  float b = _GLF_uniform_float_values[1];"
        + "  int c = _GLF_uniform_int_values[0];"
        + "  int d = _GLF_uniform_int_values[1];"
        + "  int e = _GLF_uniform_int_values[2];"
        + "  int f = _GLF_uniform_uint_values[0];"
        + "  int g = _GLF_uniform_uint_values[1];"
        + "}";

    final String shaderPrettyPrinted = ""
        + "#define _uint_2 _GLF_uniform_uint_values[0]\n"
        + "#define _uint_11 _GLF_uniform_uint_values[1]\n"
        + "#define _uint_22 _GLF_uniform_uint_values[2]\n"
        + "#define _float_0_0 _GLF_uniform_float_values[0]\n"
        + "#define _float_1_4 _GLF_uniform_float_values[1]\n"
        + "#define _float_22_2 _GLF_uniform_float_values[2]\n"
        + "\n"
        + "// Contents of a: [0, 1, 2]\n"
        + "// Contents of b: [3, 4, 5]\n"
        + "// Contents of Z: 77\n"
        + "uniform int a[3], b[2], X[1], Y[22], Z[1];\n"
        + "\n"
        + "// Contents of _GLF_uniform_float_values: [0.0, 1.4, 22.2]\n"
        + "uniform float _GLF_uniform_float_values[3], D;\n"
        + "\n"
        + "// Contents of _GLF_uniform_uint_values: [2, 11, 22]\n"
        + "uniform uint _GLF_uniform_uint_values[2], E[33];\n"
        + "\n"
        + "// Contents of test2: 66\n"
        + "uniform int test[5], test2[1], A;\n"
        + "\n"
        + "void main()\n"
        + "{\n"
        + " float a = _float_0_0;\n"
        + " float b = _float_1_4;\n"
        + " int c = _GLF_uniform_int_values[0];\n"
        + " int d = _GLF_uniform_int_values[1];\n"
        + " int e = _GLF_uniform_int_values[2];\n"
        + " int f = _uint_2;\n"
        + " int g = _uint_11;\n"
        + "}\n";

    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(ParseHelper.parse(shader, ShaderKind.FRAGMENT));

    UniformValueSupplier uniformValues = name -> {
      if (name.equals("a")) {
        return Optional.of(Arrays.asList("0", "1", "2"));
      } else if (name.equals("b")) {
        return Optional.of(Arrays.asList("3", "4", "5"));
      } else if (name.equals("_GLF_uniform_uint_values")) {
        return Optional.of(Arrays.asList("2", "11", "22"));
      } else if (name.equals("_GLF_uniform_float_values")) {
        return Optional.of(Arrays.asList("0.0", "1.4", "22.2"));
      } else if (name.equals("test2")) {
        return Optional.of(Arrays.asList("66"));
      } else if (name.equals("Z")) {
        return Optional.of(Arrays.asList("77"));
      }
      return Optional.empty();
    };

    assertEquals(shaderPrettyPrinted,
        PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shader), uniformValues));
  }

  @Test
  public void testArraySizeExpressionInParameter() throws Exception {
    final String program = ""
        + "void foo(int A[3 + 4])\n"
        + "{\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " int a[7];\n"
        + " foo(a);\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

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
    // const volatile is not useful here, but this is just to test that qualifier order is
    // preserved.
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
        + "};\n"
        + "layout(local_size_x = 128, local_size_y = 1) in;\n"
        + "void main()\n"
        + "{\n"
        + " for(uint d = gl_WorkGroupSize.x / 2u; d > 0u; d >>= 1u)\n"
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
  public void testParseAndPrintArrayParameter() throws Exception {
    final String program = "void foo(int A[2])\n"
        + "{\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintArrayVariable() throws Exception {
    final String program = "void main()\n"
        + "{\n"
        + " int A[2] = int[2](1, 2);\n"
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
  public void testParseAndPrintVersionEs() throws Exception {
    final String program = "#\tversion 320 es\nvoid main() { }\n";
    final String expected = "#version 320 es\nvoid main()\n{\n}\n";
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

  /**
   * To allow testing of the 'emitShader' method, this parses a shader from the given string,
   * invokes 'emitShader' on the resulting parsed shader, and returns the result as a string.
   */
  private String getStringViaEmitShader(String shader)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrettyPrinterVisitor.emitShader(ParseHelper.parse(shader), Optional.empty(),
        new PrintStream(bytes), PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
        PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER);
    return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  public void testNoMacrosUsedSoNoGraphicsFuzzHeader() throws Exception {
    assertFalse(getStringViaEmitShader("void main() { }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testNoMacrosUsedSoNoGraphicsFuzzHeader2() throws Exception {
    // Even though this uses a macro name, it doesn't use it as a function invocation.
    assertFalse(getStringViaEmitShader("void main() { int " + Constants.GLF_FUZZED + "; }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToIdentityMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_IDENTITY + "(1, 1); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToZeroMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_ZERO + "(0); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToOneMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_ONE + "(1); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToFalseMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_FALSE + "(false); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToTrueMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_TRUE + "(true); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToFuzzedMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { " + Constants.GLF_FUZZED + "(1234); }")
        .contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToDeadByConstructionMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { if(" + Constants.GLF_DEAD + "(false)) { } "
        + "}").contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToLoopWrapperMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { while(" + Constants.GLF_WRAPPED_LOOP
        + "(false))"
        + " {"
        + " } "
        + "}").contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToIfFalseWrapperMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { if(" + Constants.GLF_WRAPPED_IF_FALSE
        + "(false)) { } "
        + "}").contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToIfTrueWrapperMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { if(" + Constants.GLF_WRAPPED_IF_TRUE
        + "(true)) { } "
        + "}").contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testGraphicsFuzzMacrosDueToSwitchMacro() throws Exception {
    assertTrue(getStringViaEmitShader("void main() { switch(" + Constants.GLF_SWITCH
        + "(0)) { case 0: break; default: break; } "
        + "}").contains(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES));
  }

  @Test
  public void testParseAndPrintVoidFormalParameter() throws Exception {

    // For simplicity, we deliberately chuck away "void" in function formal parameters; this test
    // captures that intent.

    final String program = ""
        + "int foo(void)\n"
        + "{\n"
        + " return 2;\n"
        + "}\n"
        + "void main(void)\n"
        + "{\n"
        + "}\n";
    final String expected = ""
        + "int foo()\n"
        + "{\n"
        + " return 2;\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + "}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintVoidActualParameter() throws Exception {

    // For simplicity, we deliberately chuck away "void" in function actual parameters; this test
    // captures that intent.

    final String program = ""
        + "void foo()\n"
        + "{\n"
        + " return 2;\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " foo(void);\n"
        + "}\n";
    final String expected = ""
        + "void foo()\n"
        + "{\n"
        + " return 2;\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " foo();\n"
        + "}\n";
    assertEquals(expected, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintLoopWithDeclarationsInside() throws Exception {
    final String program = ""
        + "void main()\n"
        + "{\n"
        + " for(int i = 0; i < 10; i ++)\n"
        + "  {\n"
        + "   int a;\n"
        + "   int b;\n"
        + "  }\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void testParseAndPrintNestedLoop() throws Exception {
    final String program = ""
        + "void main()\n"
        + "{\n"
        + " for(int i = 0; i < 10; i ++)\n"
        + "  {\n"
        + "   for(int i = 0; i < 10; i ++)\n"
        + "    {\n"
        + "     int a;\n"
        + "     int b;\n"
        + "    }\n"
        + "  }\n"
        + "}\n";
    assertEquals(program, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(program
    )));
  }

  @Test
  public void addBracesParameterTest() throws Exception {
    final String frag =
          "void main(void)\n"
        + "{\n"
        + "  if (condition)\n"
        + "  do_something();\n"
        + "}\n";
    final String expected =
          "void main(void)\n"
        + "{\n"
        + "  if (condition) {\n"
        + "  do_something();\n"
        + "  }\n"
        + "}\n";

    final File fragFile = temporaryFolder.newFile("shader.frag");
    final File outFile = temporaryFolder.newFile("out.frag");
    FileUtils.writeStringToFile(fragFile, frag, StandardCharsets.UTF_8);

    PrettyPrint.main(new String[] {
        fragFile.getAbsolutePath(),
        outFile.getAbsolutePath(),
        "--add-braces" });

    final String prettiedFrag = FileUtils.readFileToString(outFile, StandardCharsets.UTF_8);

    CompareAstsDuplicate.assertEqualAsts(prettiedFrag, expected);
  }

  @Test
  public void multiDimensionalArraysTest() throws Exception {
    final String shader =
        "#version 320 es\n"
            + "const int N = 2;\n"
            + "\n"
            + "const int M = 3;\n"
            + "\n"
            + "void foo(int p[4][4][3])\n"
            + "{\n"
            + " int temp[2][M] = int[N][3](int[3](1, 2, 3), p[0][0]);\n"
            + " temp[0] = int[3](1, 2, 3);\n"
            + "}\n";
    assertEquals(shader, PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shader)));
  }

  @Test
  public void testEmitGraphicsFuzzDefines() throws Exception {

    final String expected = "\n"
        + "#ifndef REDUCER\n"
        + "#define _GLF_ZERO(X, Y)                   (Y)\n"
        + "#define _GLF_ONE(X, Y)                    (Y)\n"
        + "#define _GLF_FALSE(X, Y)                  (Y)\n"
        + "#define _GLF_TRUE(X, Y)                   (Y)\n"
        + "#define _GLF_IDENTITY(X, Y)               (Y)\n"
        + "#define _GLF_DEAD(X)                      (X)\n"
        + "#define _GLF_FUZZED(X)                    (X)\n"
        + "#define _GLF_WRAPPED_LOOP(X)              X\n"
        + "#define _GLF_WRAPPED_IF_TRUE(X)           X\n"
        + "#define _GLF_WRAPPED_IF_FALSE(X)          X\n"
        + "#define _GLF_SWITCH(X)                    X\n"
        + "#define _GLF_MAKE_IN_BOUNDS_INT(IDX, SZ)  ((IDX) < 0 ? 0 "
            + ": ((IDX) >= SZ ? SZ - 1 : (IDX)))\n"
        + "#define _GLF_MAKE_IN_BOUNDS_UINT(IDX, SZ)  ((IDX) >= SZ ? SZ - 1u : (IDX))\n"
        + "#endif\n"
        + "\n"
        + "// END OF GENERATED HEADER\n";

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(bytes);
    PrettyPrinterVisitor.emitGraphicsFuzzDefines(printStream, ShadingLanguageVersion.ESSL_100);
    assertEquals(expected, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
  }

}
