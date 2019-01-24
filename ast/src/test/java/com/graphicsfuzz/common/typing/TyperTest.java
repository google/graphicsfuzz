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

package com.graphicsfuzz.common.typing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TyperTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void visitMemberLookupExpr() throws Exception {

    String prog = "struct S { float a; float b; };\n"
          + "struct T { S s; float c; };\n"
          + "void main() {\n"
          + "  T myT = T(S(1.0, 2.0), 3.0);\n"
          + "  myT.s.a = myT.c;\n"
          + "}";

    TranslationUnit tu = ParseHelper.parse(prog);

    int actualCount =
          new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_100) {

            private int count;

            public int getCount() {
              return count;
            }

            @Override
            public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
              super.visitMemberLookupExpr(memberLookupExpr);
              assertNotNull(lookupType(memberLookupExpr));
              count++;
            }
          }.getCount();

    assertEquals(3, actualCount);

  }

  @Test
  public void visitMemberLookupExprAnonymous() throws Exception {

    String prog = "struct { float a; float b; } myStruct;\n"
        + "void main() {\n"
        + "  myStruct.a = 2.0;\n"
        + "  myStruct.b = 3.0;\n"
        + "  myStruct.a = myStruct.b;\n"
        + "}";

    TranslationUnit tu = ParseHelper.parse(prog);

    int actualCount =
        new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_100) {

          private int count;

          public int getCount() {
            return count;
          }

          @Override
          public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
            super.visitMemberLookupExpr(memberLookupExpr);
            assertEquals(BasicType.FLOAT, lookupType(memberLookupExpr).getWithoutQualifiers());
            count++;
          }
        }.getCount();

    assertEquals(4, actualCount);

  }

  @Test
  public void testTypeOfScalarConstructors() throws Exception {
    String program = "void main() { float(1); int(1); uint(1); bool(1); }";

    for (BasicType b : Arrays.asList(BasicType.FLOAT, BasicType.INT, BasicType.UINT,
          BasicType.BOOL)) {

      try {

        new NullCheckTyper(ParseHelper.parse(program), ShadingLanguageVersion.GLSL_440) {

          @Override
          public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
            super.visitTypeConstructorExpr(typeConstructorExpr);
            if (lookupType(typeConstructorExpr) == b) {
              throw new RuntimeException("got_type");
            }
          }

        };

      } catch (RuntimeException re) {
        if (re.getMessage().equals("got_type")) {
          continue;
        }
        throw re;
      }

      assertFalse("Should not get here", true);

    }

  }

  @Test
  public void testMemberLookupTypeFloat() throws Exception {
    final String program = "void main() { vec2 v2 = vec2(1.0);"
          + " v2.x; v2.y;"
          + " vec3 v3 = vec3(1.0);"
          + " v3.x; v3.y; v3.z;"
          + " vec4 v4 = vec4(1.0);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_100) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.FLOAT, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testMemberLookupTypeInt() throws Exception {
    final String program = "void main() { ivec2 v2 = ivec2(1);"
          + " v2.x; v2.y;"
          + " ivec3 v3 = ivec3(1);"
          + " v3.x; v3.y; v3.z;"
          + " ivec4 v4 = ivec4(1);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.INT, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testMemberLookupTypeUint() throws Exception {
    final String program = "void main() { uvec2 v2 = uvec2(1u);"
          + " v2.x; v2.y;"
          + " uvec3 v3 = uvec3(1u);"
          + " v3.x; v3.y; v3.z;"
          + " uvec4 v4 = uvec4(1u);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.UINT, lookupType(memberLookupExpr));
      }
    };

  }


  @Test
  public void testMemberLookupTypeBool() throws Exception {
    final String program = "void main() { bvec2 v2 = bvec2(true);"
          + " v2.x; v2.y;"
          + " bvec3 v3 = bvec3(true);"
          + " v3.x; v3.y; v3.z;"
          + " bvec4 v4 = bvec4(true);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.BOOL, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testBooleanVectorType() throws Exception {
    final String program = "void main() { vec3(1.0) > vec3(2.0); }";
    TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440) {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        assertEquals(BasicType.BVEC3, lookupType(binaryExpr));
      }
    };
  }

  @Test
  public void testBooleanVectorType2() throws Exception {
    final String program = "void main() { vec3(1.0) > 2.0; }";
    TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440) {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        assertEquals(BasicType.BVEC3, lookupType(binaryExpr));
      }
    };
  }

  @Test
  public void testSwizzleTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() { vec2 v; v.xy = v.yx; }");
    Typer typer = new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_100);
    new StandardVisitor() {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(typer.lookupType(memberLookupExpr), BasicType.VEC2);
      }
    }.visit(tu);
  }

  @Test
  public void testAssignTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() {"
          + "int x;"
          + "x = 2;"
          + "float f;"
          + "f += 2.0;"
          + "vec2 v2;"
          + "v2 *= v2;"
          + "vec3 v3;"
          + "v3 /= v3;"
          + "ivec2 i2;"
          + "i2 -= i2; }");
    Typer typer = new NullCheckTyper(tu, ShadingLanguageVersion.GLSL_440);
    new StandardVisitor() {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        switch (binaryExpr.getOp()) {
          case ASSIGN:
            assertEquals(BasicType.INT, typer.lookupType(binaryExpr));
            break;
          case ADD_ASSIGN:
            assertEquals(BasicType.FLOAT, typer.lookupType(binaryExpr));
            break;
          case SUB_ASSIGN:
            assertEquals(BasicType.IVEC2, typer.lookupType(binaryExpr));
            break;
          case MUL_ASSIGN:
            assertEquals(BasicType.VEC2, typer.lookupType(binaryExpr));
            break;
          case DIV_ASSIGN:
            assertEquals(BasicType.VEC3, typer.lookupType(binaryExpr));
            break;
          default:
            assertTrue(false);
        }
      }
    }.visit(tu);
  }

  @Test
  public void testGlPositionTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() { gl_Position = vec4(0.0); }");
    Typer typer = new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_POSITION)) {
          assertEquals(BasicType.VEC4, typer.lookupType(variableIdentifierExpr));
        }
      }
    }.visit(tu);

  }

  @Test
  public void testGlPointSizeTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() { gl_PointSize = 1.0; }");
    Typer typer = new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_POINT_SIZE)) {
          assertEquals(BasicType.FLOAT, typer.lookupType(variableIdentifierExpr));
        }
      }
    }.visit(tu);

  }

  @Test
  public void testGlNumWorkGroupsTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_NumWorkGroups",
        OpenGlConstants.GL_NUM_WORK_GROUPS,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGLWorkGroupSizeTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_WorkGroupSize",
        OpenGlConstants.GL_WORK_GROUP_SIZE,
        BasicType.UVEC3,
        TypeQualifier.CONST);
  }

  @Test
  public void testGlWorkGroupIDTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_WorkGroupID",
        OpenGlConstants.GL_WORK_GROUP_ID,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGlLocalInvocationIDTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_LocalInvocationID",
        OpenGlConstants.GL_LOCAL_INVOCATION_ID,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGlGlobalInvocationIDTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_GlobalInvocationID",
        OpenGlConstants.GL_GLOBAL_INVOCATION_ID,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGlLocalInvocationIndexTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_LocalInvocationIndex",
        OpenGlConstants.GL_LOCAL_INVOCATION_INDEX,
        BasicType.UINT,
        TypeQualifier.SHADER_INPUT);
  }
  @Test
  public void testOctalIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 031;"
        + "}");
    new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300) {
      @Override
      public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
        super.visitScalarInitializer(scalarInitializer);
        assertSame(lookupType(scalarInitializer.getExpr()), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void testHexIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 0xA03B;"
        + "}");
    new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300) {
      @Override
      public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
        super.visitScalarInitializer(scalarInitializer);
        assertSame(lookupType(scalarInitializer.getExpr()), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void testOctalUnsignedIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 031u;"
        + "}");
    new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300) {
      @Override
      public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
        super.visitScalarInitializer(scalarInitializer);
        assertSame(lookupType(scalarInitializer.getExpr()), BasicType.UINT);
      }
    }.visit(tu);
  }

  @Test
  public void testHexUnsignedIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 0xA03Bu;"
        + "}");
    new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_300) {
      @Override
      public void visitScalarInitializer(ScalarInitializer scalarInitializer) {
        super.visitScalarInitializer(scalarInitializer);
        assertSame(lookupType(scalarInitializer.getExpr()), BasicType.UINT);
      }
    }.visit(tu);
  }

  private void checkComputeShaderBuiltin(String builtin, String builtinConstant, BasicType baseType,
      TypeQualifier qualifier) throws IOException, ParseTimeoutException, InterruptedException {
    TranslationUnit tu = ParseHelper.parse("void main() { " + builtin + "; }");
    Typer typer = new NullCheckTyper(tu, ShadingLanguageVersion.ESSL_310);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(builtinConstant)) {
          assertEquals(
              new QualifiedType(baseType,
                  Arrays.asList(qualifier)), typer.lookupType(variableIdentifierExpr));
        }
      }
    }.visit(tu);
  }

  class NullCheckTyper extends Typer {

    public NullCheckTyper(IAstNode node,
          ShadingLanguageVersion shadingLanguageVersion) {
      super(node, shadingLanguageVersion);
    }

    @Override
    public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
      super.visitFunctionCallExpr(functionCallExpr);
      assertNotNull(lookupType(functionCallExpr));
    }

    @Override
    public void visitParenExpr(ParenExpr parenExpr) {
      super.visitParenExpr(parenExpr);
      assertNotNull(lookupType(parenExpr));
    }

    @Override
    public void visitUnaryExpr(UnaryExpr unaryExpr) {
      super.visitUnaryExpr(unaryExpr);
      assertNotNull(lookupType(unaryExpr));
    }

    @Override
    public void visitBinaryExpr(BinaryExpr binaryExpr) {
      super.visitBinaryExpr(binaryExpr);
      assertNotNull(lookupType(binaryExpr));
    }

    @Override
    public void visitTernaryExpr(TernaryExpr ternaryExpr) {
      super.visitTernaryExpr(ternaryExpr);
      assertNotNull(lookupType(ternaryExpr));
    }

    @Override
    public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
      super.visitVariableIdentifierExpr(variableIdentifierExpr);
      assertNotNull(lookupType(variableIdentifierExpr));
    }

    @Override
    public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
      super.visitArrayIndexExpr(arrayIndexExpr);
      assertNotNull(lookupType(arrayIndexExpr));
    }

    @Override
    public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
      super.visitTypeConstructorExpr(typeConstructorExpr);
      assertNotNull(lookupType(typeConstructorExpr));
    }

    @Override
    public void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr) {
      super.visitBoolConstantExpr(boolConstantExpr);
      assertNotNull(lookupType(boolConstantExpr));
    }

    @Override
    public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
      super.visitIntConstantExpr(intConstantExpr);
      assertNotNull(lookupType(intConstantExpr));
    }

    @Override
    public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
      super.visitFloatConstantExpr(floatConstantExpr);
      assertNotNull(lookupType(floatConstantExpr));
    }

    @Override
    public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
      super.visitMemberLookupExpr(memberLookupExpr);
      assertNotNull(lookupType(memberLookupExpr));
    }
  }

  @Test
  public void testBuiltinsEssl100() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_100;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsEssl300() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_300;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsEssl310() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_310;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl110() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_110;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl120() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_120;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl130() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_130;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl140() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_140;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl150() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_150;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl330() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_330;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl400() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_400;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl410() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_410;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl420() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_420;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl430() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_430;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl440() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_440;
    testBuiltins(shadingLanguageVersion);
  }

  @Test
  public void testBuiltinsGlsl450() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.GLSL_450;
    testBuiltins(shadingLanguageVersion);
  }

  private void testBuiltins(ShadingLanguageVersion shadingLanguageVersion)
      throws IOException, InterruptedException {
    final File tempFile = temporaryFolder.newFile("shader.frag");
    FileUtils.writeStringToFile(
        tempFile,
        makeBuiltinsProgram(shadingLanguageVersion).toString(),
        StandardCharsets.UTF_8);
    final ExecResult result = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, tempFile);
    assertEquals(0, result.res);
  }

  private StringBuilder makeBuiltinsProgram(ShadingLanguageVersion shadingLanguageVersion) {
    StringBuilder result = new StringBuilder();
    result.append("#version " + shadingLanguageVersion.getVersionString() + "\n");
    result.append("#ifdef GL_ES\n");
    result.append("precision mediump float;\n");
    result.append("#endif\n");
    int counter = 0;
    for (String name : TyperHelper.getBuiltins(shadingLanguageVersion).keySet()) {
      for (FunctionPrototype fp : TyperHelper.getBuiltins(shadingLanguageVersion).get(name)) {
        counter++;
        result.append(fp.getReturnType() + " test" + counter + "_" + fp.getName() + "(");
        boolean first = true;
        for (ParameterDecl decl : fp.getParameters()) {
          if (!first) {
            result.append(", ");
          }
          first = false;
          result.append(decl.getType() + " " + decl.getName());
        }
        result.append(") {\n");
        result.append("  return " + fp.getName() + "(");
        first = true;
        for (ParameterDecl decl : fp.getParameters()) {
          if (!first) {
            result.append(", ");
          }
          first = false;
          result.append(decl.getName());
        }
        result.append(");\n");

        result.append("}\n\n");
      }
    }
    return result;
  }

}





