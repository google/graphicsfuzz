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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.util.Constants;
import org.junit.Test;

public class MakeArrayAccessesInBoundsTest {

  @Test
  public void testBasic() throws Exception {
    final String shader = "#version 300 es\nvoid main() { int A[5]; int x = 17; A[x] = 2; }";
    final String expected = "#version 300 es\nvoid main() { int A[5]; int x = 17; A["
        + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 5)] = 2; }";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testMatrixVector() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() {"
        + "  mat4x2 As[5];"
        + "  int x = 17;"
        + "  int y = -22;"
        + "  int z = 100;"
        + "  As[x][y][z] = 2.0;"
        + "}";
    final String expected = "#version 300 es\n"
        + "void main() {"
        + "  mat4x2 As[5];"
        + "  int x = 17;"
        + "  int y = -22;"
        + "  int z = 100;"
        + "  As[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 5)]"
        + "    /* column */ [" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(y, 4)]"
        + "    /* row */ [" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(z, 2)] = 2.0;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testMatrixVector2() throws Exception {
    final String shader = "#version 300 es\n"
          + "void main() { mat3x4 As[5];"
          + "  int x = 17;"
          + "  int y = -22;"
          + "  int z = 100;"
          + "  mat3x4 A = As[x];"
          + "  vec4 v;"
          + "  v = A[y];"
          + "  float f;"
          + "  f = v[z];"
          + "}";
    final String expected = "#version 300 es\n"
          + "void main() { mat3x4 As[5];"
          + "  int x = 17;"
          + "  int y = -22;"
          + "  int z = 100;"
          + "  mat3x4 A = As[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 5)];"
          + "  vec4 v;"
          + "  v = A[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(y, 3)];"
          + "  float f;"
          + "  f = v[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(z, 4)];"
          + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
          PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testUIntConstantExprIndex() throws Exception {
    final String shader = "#version 300 es\n"
        + "void main() { vec3 stuff[16];"
        + "  uint x = 19u;"
        + "  vec3 f = stuff[x];"
        + "}";
    final String expected = "#version 300 es\n"
        + "void main() { vec3 stuff[16];"
        + "  uint x = 19u;"
        + "  vec3 f = stuff[" + Constants.GLF_MAKE_IN_BOUNDS_UINT + "(x, 16u)];"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testUIntFunctionCallReturnIndex() throws Exception {
    final String shader = "#version 310 es\n"
        + "void main() { vec3 stuff[16];"
        + "  uint uselessOut;"
        + "  vec3 f = stuff[uaddCarry(19u, 15u, uselessOut)];"
        + "}";
    final String expected = "#version 310 es\n"
        + "void main() { vec3 stuff[16];"
        + "  uint uselessOut;"
        + "  vec3 f = stuff[" + Constants.GLF_MAKE_IN_BOUNDS_UINT + "(uaddCarry(19u, 15u, "
        + "uselessOut), 16u)];"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(expected)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testUIntStaticallyInBounds() throws Exception {
    final String shader = "#version 310 es\n"
        + "void main() { float stuff[16];"
        + "  stuff[3u] = 1.0;"
        + "}";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    assertEquals(PrettyPrinterVisitor.prettyPrintAsString(ParseHelper.parse(shader)),
        PrettyPrinterVisitor.prettyPrintAsString(tu));
  }

  @Test
  public void testMakeStructMemberArrayAccessInBounds1() throws Exception {
    final String shader = "#version 310 es\n"
        + "struct S {\n"
        + "  int A[3];\n"
        + "};\n"
        + "void main() {\n"
        + "  int x;\n"
        + "  x = 22;\n"
        + "  S myS;\n"
        + "  myS.A[x] = 6;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "struct S {\n"
        + "  int A[3];\n"
        + "};\n"
        + "void main() {\n"
        + "  int x;\n"
        + "  x = 22;\n"
        + "  S myS;\n"
        + "  myS.A[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 3)] = 6;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testMakeStructMemberArrayAccessInBounds2() throws Exception {
    final String shader = "#version 310 es\n"
        + "const int N = 3;\n"
        + "struct S {\n"
        + "  int A[N];\n"
        + "};\n"
        + "void main() {\n"
        + "  int x;\n"
        + "  x = 22;\n"
        + "  S myS;\n"
        + "  myS.A[x] = 6;\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "const int N = 3;\n"
        + "struct S {\n"
        + "  int A[N];\n"
        + "};\n"
        + "void main() {\n"
        + "  int x;\n"
        + "  x = 22;\n"
        + "  S myS;\n"
        + "  myS.A[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 3)] = 6;\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testMakeArrayParameterAccessInBounds1() throws Exception {
    final String shader = "#version 310 es\n"
        + "int foo(int A[3], int x) {\n"
        + "  return A[x];\n"
        + "}\n"
        + "void main() {\n"
        + "  int A[3];\n"
        + "  foo(A, 7);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "int foo(int A[3], int x) {\n"
        + "  return A[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 3)];\n"
        + "}\n"
        + "void main() {\n"
        + "  int A[3];\n"
        + "  foo(A, 7);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

  @Test
  public void testMakeArrayParameterAccessInBounds2() throws Exception {
    final String shader = "#version 310 es\n"
        + "const int N = 3;\n"
        + "int foo(int A[N], int x) {\n"
        + "  return A[x];\n"
        + "}\n"
        + "void main() {\n"
        + "  int A[N];\n"
        + "  foo(A, 7);\n"
        + "}\n";
    final String expected = "#version 310 es\n"
        + "const int N = 3;\n"
        + "int foo(int A[N], int x) {\n"
        + "  return A[" + Constants.GLF_MAKE_IN_BOUNDS_INT + "(x, 3)];\n"
        + "}\n"
        + "void main() {\n"
        + "  int A[N];\n"
        + "  foo(A, 7);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    MakeArrayAccessesInBounds.makeInBounds(tu);
    CompareAsts.assertEqualAsts(expected, tu);
  }

}
