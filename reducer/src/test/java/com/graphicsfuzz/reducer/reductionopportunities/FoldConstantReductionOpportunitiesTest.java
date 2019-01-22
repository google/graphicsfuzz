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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FoldConstantReductionOpportunitiesTest {

  @Test
  public void testSin0() throws Exception {
    check("void main() { sin(0.0); }", 1, "void main() { 0.0; }");
  }

  @Test
  public void testCos0() throws Exception {
    check("void main() { cos(0.0); }", 1, "void main() { 1.0; }");
  }

  @Test
  public void testAdd0() throws Exception {
    check(
        "void main() {"
        + "float a;"
        + "vec2 b;"
        + "vec3 c;"
        + "vec4 d;"
        + "mat2 e;"
        + "mat3 f;"
        + "mat4 g;"
        + "a + 0.;"
        + "b + 0.0;"
        + "vec2(0.0) + b;"
        + "c + vec3(0.0, 0.0, 0.0);"
        + "vec4(0.) + d;"
        + "e + 0.0;"
        + "mat2(0.0) + e;"
        + "f + mat3x3(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);"
        + "mat4x4(0.) + g;"
        + "}",
        9,
        "void main() {"
        + "float a;"
        + "vec2 b;"
        + "vec3 c;"
        + "vec4 d;"
        + "mat2 e;"
        + "mat3 f;"
        + "mat4 g;"
        + "a;"
        + "b;"
        + "b;"
        + "c;"
        + "d;"
        + "e;"
        + "e;"
        + "f;"
        + "g;"
        + "}");
  }

  @Test
  public void testAdd0Vec() throws Exception {
    // We do not want to turn "x + vec2(0.0)" into "x", because adding the vector to
    // x leads to something of type vec2.  Similar cases apply to other types; let's
    // just test this one as an example.
    final String prog = "void main() { float x; x + vec2(0.0); }";
    check(prog, 0, prog);
  }

  @Test
  public void testMul0() throws Exception {
    check(
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "a * 0.;"
            + "b * 0.0;"
            + "vec2(0.0) * b;"
            + "c * vec3(0.0, 0.0, 0.0);"
            + "vec4(0.) * d;"
            + "e * 0.0;"
            + "mat2(0.0) * e;"
            + "f * mat3x3(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);"
            + "mat4x4(0.) * g;"
            + "}",
        9,
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "0.0;"
            + "}");
  }

  @Test
  public void testMulIdentity() throws Exception {
    check(
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "a * 1.;"
            + "b * 1.0;"
            + "vec2(1.0) * b;"
            + "c * vec3(1.0, 1.0, 1.0);"
            + "vec4(1.) * d;"
            + "e * 1.0;"
            + "mat2(1.0) * e;"
            + "f * mat3x3(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0);"
            + "mat4x4(1.) * g;"
            + "mat4x4(1.,  0.0, 0. , 0.0, "
            + "       0.,  1. , 0.0, 0.0, "
            + "       0.0, 0.0, 1. , 0. , "
            + "       0.0, 0.0, 0.0, 1.0"
            + "      ) * g;"
            + "mat4x4(1.,  0.0, 0. , 0.0, " // This one is not an identity matrix due to ".1"
            + "       0.,  1. , 0.0, 0.0, "
            + "       0.0, 0.0, .1 , 0. , "
            + "       0.0, 0.0, 0.0, 1.0"
            + "      ) * g;"
            + "}",
        10,
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "a;"
            + "b;"
            + "b;"
            + "c;"
            + "d;"
            + "e;"
            + "e;"
            + "f;"
            + "g;"
            + "g;"
            + "mat4x4(1.,  0.0, 0. , 0.0, "
            + "       0.,  1. , 0.0, 0.0, "
            + "       0.0, 0.0, .1 , 0. , "
            + "       0.0, 0.0, 0.0, 1.0"
            + "      ) * g;"
            + "}");
  }

  @Test
  public void testDiv1() throws Exception {
    check(
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "a / 1.;"
            + "1. / a;"
            + "b / 1.0;"
            + "vec2(1.0) / b;"
            + "b / vec2(1.0);"
            + "vec2(1.0) / b;"
            + "c / vec3(1.0, 1.0, 1.0);"
            + "vec3(1.0, 1.0, 1.0) / c;"
            + "d / vec4(1.);"
            + "vec4(1.) / d;"
            + "a / vec4(1.);"
            + "}",
        5,
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "a;"
            + "1. / a;"
            + "b;"
            + "vec2(1.0) / b;"
            + "b;"
            + "vec2(1.0) / b;"
            + "c;"
            + "vec3(1.0, 1.0, 1.0) / c;"
            + "d;"
            + "vec4(1.) / d;"
            + "a / vec4(1.);"
            + "}");
  }

  @Test
  public void testSub0() throws Exception {
    check(
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "a - 0.;"
            + "0. - a;"
            + "b - 0.0;"
            + "vec2(0.0) - b;"
            + "b - vec2(0.0);"
            + "vec2(0.0) - b;"
            + "c - vec3(0.0, 0.0, 0.0);"
            + "vec3(0.0, 0.0, 0.0) - c;"
            + "d - vec4(0.);"
            + "vec4(0.) - d;"
            + "e - mat2(0.0);"
            + "mat2(0.0) - e;"
            + "e - 0.0;"
            + "0.0 - e;" // Should not be simplified to -0.0.
            + "f - mat3(vec3(0.0), vec3(0.0, 0.0, 0.0), vec3(0.0));"
            + "mat3(0.0) - f;"
            + "g - mat4(vec4(0.0), vec4(0.0), vec4(0.0), vec4(0.0));"
            + "mat4(0.0) - g;"
            + "0.0 - g;"
            + "a - mat4(0.0);"
            + " mat3(0.0) - a;"
            + "}",
        19,
        "void main() {"
            + "float a;"
            + "vec2 b;"
            + "vec3 c;"
            + "vec4 d;"
            + "mat2 e;"
            + "mat3 f;"
            + "mat4 g;"
            + "a;"
            + "(- a);"
            + "b;"
            + "(- b);"
            + "b;"
            + "(- b);"
            + "c;"
            + "(- c);"
            + "d;"
            + "(- d);"
            + "e;"
            + "(- e);"
            + "e;"
            + "(- e);"
            + "f;"
            + "(- f);"
            + "g;"
            + "(- g);"
            + "(- g);"
            + "a - mat4(0.0);" // Should not be simplified to a
            + " mat3(0.0) - a;" // Should not be simplified to -a
            + "}");
  }

  @Test
  public void testUnaryPlusOrMinusZeroToZero() throws Exception {
    check("void main() { -0.0; +0.0; -0; +0; }", 4, "void main() { 0.0; 0.0; 0; 0; }");
  }

  @Test
  public void testRemoveParens() throws Exception {
    check("void main() {" +
            "int x;" +
            "vec2 v;" +
            "(x);" +
            "(x + y) * z;" +
            "(v).x;" +
            "(v.x);" +
            "(v + vec2(2.0)).x;" +
            "(1.0);" +
            "(vec2(1.0));" +
            "(sin(3.0));" +
            "}",
        6,
        "void main() {" +
            "int x;" +
            "vec2 v;" +
            "x;" +
            "(x + y) * z;" +
            "v.x;" +
            "v.x;" +
            "(v + vec2(2.0)).x;" +
            "1.0;" +
            "vec2(1.0);" +
            "sin(3.0);" +
            "}"
        );
  }

  @Test
  public void testSimplifyVectorLookup() throws Exception {
    check("" +
            "int glob;" +
            "uint foo() { glob++; return 0u; }" +
            "void main() {" +
            "int a, b, c;" +
            "float d, e, f;" +
            "uint g, h, i;" +
            "vec2(1.0, 0.0).x;" +
            "vec3(1.0, d, f).g;" +
            "ivec4(5, 2, a, b + 2).q;" +
            "ivec3(a++, 2, 3, 4).t;" +
            "uvec4(g, h, 5u, 3u).w;" +
            "uvec4(foo(), h, 5u, 3u).w;" +
            "}",
            4,
        "int glob;" +
            "uint foo() { glob++; return 0u; }" +
            "void main() {" +
            "int a, b, c;" +
            "float d, e, f;" +
            "uint g, h, i;" +
            "(1.0);" +
            "(d);" +
            "(b + 2);" +
            "ivec3(a++, 2, 3, 4).t;" +
            "(3u);" +
            "uvec4(foo(), h, 5u, 3u).w;" +
            "}");
  }

  @Test
  public void testFpMul() throws Exception {
    check("void main() { 2.0*3.0; }", 1, "void main() { 6.0; }");
  }

  @Test
  public void testFpAdd() throws Exception {
    check("void main() { 2.0+3.0; }", 1, "void main() { 5.0; }");
  }

  @Test
  public void testFpSub() throws Exception {
    check("void main() { 2.0-3.0; }", 1, "void main() { -1.0; }");
  }

  @Test
  public void testVecScalarMul1() throws Exception {
    check("void main() { vec3(10.0, 11.0, 12.0) * 10.0; }",
        1,
        "void main() { vec3(100.0, 110.0, 120.0); }");
  }

  @Test
  public void testVecScalarAdd1() throws Exception {
    check("void main() { vec3(0.02, 0.02, 0.025) + 10.0; }",
        1,
        "void main() { vec3(10.02, 10.02, 10.025); }");
  }

  @Test
  public void testVecScalarSub1() throws Exception {
    check("void main() { vec3(0.02, 0.02, 0.025) - 10.0; }",
        1,
        "void main() { vec3(-9.98, -9.98, -9.975); }");
  }

  @Test
  public void testVecScalarMul2() throws Exception {
    check("void main() { 10.0 * vec3(10.0, 11.0, 12.0); }",
        1,
        "void main() { vec3(100.0, 110.0, 120.0); }");
  }

  @Test
  public void testVecScalarAdd2() throws Exception {
    check("void main() { 10.0 + vec3(0.02, 0.02, 0.025); }",
        1,
        "void main() { vec3(10.02, 10.02, 10.025); }");
  }

  @Test
  public void testVecScalarSub2() throws Exception {
    check("void main() { 10.0 - vec3(0.02, 0.02, 0.025); }",
        1,
        "void main() { vec3(9.98, 9.98, 9.975); }");
  }

  @Test
  public void testFoldOctalInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 012 + 017; }",
        1,
        "void main() { 25; }");
  }

  @Test
  public void testFoldHexInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 0xAB + 0xCd; }",
        1,
        "void main() { 376; }");
  }

  @Test
  public void testFoldOctalUInt() throws Exception {
    // Results for unsigned integer constant folding are in decimal.
    check("void main() { 026u + 073u; }",
        1,
        "void main() { 81u; }");
  }

  @Test
  public void testFoldHexUInt() throws Exception {
    // Results for unsigned integer constant folding are in decimal.
    check("void main() { 0xFEF + 0xeFe; }",
        1,
        "void main() { 7917; }");
  }

  @Test
  public void testFoldDecimalAndHexInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 1 + 0xF; }",
        1,
        "void main() { 16; }");
  }

  @Test
  public void testFoldHexAndDecimalInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 0xAbCD + 17; }",
        1,
        "void main() { 43998; }");
  }

  @Test
  public void testFoldDecimalAndOctalInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 1 + 077; }",
        1,
        "void main() { 64; }");
  }

  @Test
  public void testFoldOctalAndDecimalInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 052 + 17; }",
        1,
        "void main() { 59; }");
  }

  @Test
  public void testFoldOctalAndHexInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 017 + 0xF; }",
        1,
        "void main() { 30; }");
  }

  @Test
  public void testFoldHexAndOctalInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 0xABCD + 0333; }",
        1,
        "void main() { 44200; }");
  }

  @Test
  public void testFoldDecimalAndHexUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 1u + 0xFu; }",
        1,
        "void main() { 16u; }");
  }

  @Test
  public void testFoldHexAndDecimalUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 0xABCDu + 17u; }",
        1,
        "void main() { 43998u; }");
  }

  @Test
  public void testFoldDecimalAndOctalUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 1u + 077u; }",
        1,
        "void main() { 64u; }");
  }

  @Test
  public void testFoldOctalAndDecimalUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 052u + 17u; }",
        1,
        "void main() { 59u; }");
  }

  @Test
  public void testFoldOctalAndHexUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 017u + 0xFu; }",
        1,
        "void main() { 30u; }");
  }

  @Test
  public void testFoldHexAndOctalUInt() throws Exception {
    // Results for integer constant folding are in decimal.
    check("void main() { 0xABCDu + 0333u; }",
        1,
        "void main() { 44200u; }");
  }


  private void check(String before, int numOps, String after) throws IOException,
      ParseTimeoutException, InterruptedException {
    final TranslationUnit tu = ParseHelper.parse(before);
    final List<SimplifyExprReductionOpportunity> ops = FoldConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReducerContext(false,
            ShadingLanguageVersion.ESSL_100, null, null, true));
    ops.forEach(item -> item.applyReduction());
    CompareAsts.assertEqualAsts(after, tu);
    assertEquals(numOps, ops.size());
  }


}
