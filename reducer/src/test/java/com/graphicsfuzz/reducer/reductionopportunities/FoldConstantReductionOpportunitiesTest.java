package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.CompareAsts;
import com.graphicsfuzz.common.util.Helper;
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

  private void check(String before, int numOps, String after) throws IOException, ParseTimeoutException {
    final TranslationUnit tu = Helper.parse(before, false);
    final List<SimplifyExprReductionOpportunity> ops = FoldConstantReductionOpportunities
        .findOpportunities(MakeShaderJobFromFragmentShader.make(tu), new ReductionOpportunityContext(false,
            ShadingLanguageVersion.ESSL_100, null, null));
    ops.forEach(item -> item.applyReduction());
    CompareAsts.assertEqualAsts(after, tu);
    assertEquals(numOps, ops.size());
  }

}