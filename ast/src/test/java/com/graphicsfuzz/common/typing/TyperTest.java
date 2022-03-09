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
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.LengthExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.UnsupportedLanguageFeatureException;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
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
  public void testTypingOfInterfaceBlockMembers() throws Exception {

    String prog = "#version 310 es\n"
        + "layout(std430, binding = 0) buffer doesNotMatter {\n"
        + "  int result;\n"
        + "  int data[];\n"
        + "};\n"
        + "void main() {"
        + "  data;\n"
        + "  result;\n"
        + "  data[2];\n"
        + "}";

    new NullCheckTyper(ParseHelper.parse(prog)) {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        final Type withoutQualifiers = lookupType(variableIdentifierExpr).getWithoutQualifiers();
        if (variableIdentifierExpr.getName().equals("data")) {
          assertTrue(withoutQualifiers instanceof ArrayType);
          final ArrayType arrayType = (ArrayType) withoutQualifiers;
          assertSame(arrayType.getBaseType(), BasicType.INT);
          assertFalse(arrayType.getArrayInfo().hasSizeExpr(0));
          assertFalse(arrayType.getArrayInfo().hasConstantSize(0));
        } else if (variableIdentifierExpr.getName().equals("result")) {
          assertSame(withoutQualifiers, BasicType.INT);
        }
      }
    };

  }

  @Test
  public void testArrayParameter() throws Exception {
    final String shader = ""
        + "int foo(int A[3 + 4])\n"
        + "{\n"
        + "  return A[0];\n"
        + "}\n"
        + "void main()\n"
        + "{\n"
        + " int a[7];\n"
        + " foo(a);\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(shader);
    new NullCheckTyper(tu) {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        assertSame(lookupType(functionCallExpr).getWithoutQualifiers(), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void visitMemberLookupExpr() throws Exception {

    String prog = "#version 100\n"
          + "struct S { float a; float b; };\n"
          + "struct T { S s; float c; };\n"
          + "void main() {\n"
          + "  T myT = T(S(1.0, 2.0), 3.0);\n"
          + "  myT.s.a = myT.c;\n"
          + "}";

    TranslationUnit tu = ParseHelper.parse(prog);

    int actualCount = new NullCheckTyper(tu) {

      private int count;

      private int getCount() {
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

    String prog = "#version 100\n"
        + "struct { float a; float b; } myStruct;\n"
        + "void main() {\n"
        + "  myStruct.a = 2.0;\n"
        + "  myStruct.b = 3.0;\n"
        + "  myStruct.a = myStruct.b;\n"
        + "}";

    TranslationUnit tu = ParseHelper.parse(prog);

    int actualCount =
        new NullCheckTyper(tu) {

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
    String program = "#version 440\n"
        + "void main() { float(1); int(1); uint(1); bool(1); }";

    for (BasicType b : Arrays.asList(BasicType.FLOAT, BasicType.INT, BasicType.UINT,
          BasicType.BOOL)) {

      try {

        new NullCheckTyper(ParseHelper.parse(program)) {

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
    final String program = "#version 100\n"
          + "void main() { vec2 v2 = vec2(1.0);"
          + " v2.x; v2.y;"
          + " vec3 v3 = vec3(1.0);"
          + " v3.x; v3.y; v3.z;"
          + " vec4 v4 = vec4(1.0);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.FLOAT, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testMemberLookupTypeInt() throws Exception {
    final String program = "#version 440\n"
          + "void main() { ivec2 v2 = ivec2(1);"
          + " v2.x; v2.y;"
          + " ivec3 v3 = ivec3(1);"
          + " v3.x; v3.y; v3.z;"
          + " ivec4 v4 = ivec4(1);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.INT, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testMemberLookupTypeUint() throws Exception {
    final String program = "#version 440\n"
          + "void main() { uvec2 v2 = uvec2(1u);"
          + " v2.x; v2.y;"
          + " uvec3 v3 = uvec3(1u);"
          + " v3.x; v3.y; v3.z;"
          + " uvec4 v4 = uvec4(1u);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.UINT, lookupType(memberLookupExpr));
      }
    };

  }


  @Test
  public void testMemberLookupTypeBool() throws Exception {
    final String program = "#version 440\n"
          + "void main() { bvec2 v2 = bvec2(true);"
          + " v2.x; v2.y;"
          + " bvec3 v3 = bvec3(true);"
          + " v3.x; v3.y; v3.z;"
          + " bvec4 v4 = bvec4(true);"
          + " v4.x; v4.y; v4.z; v4.w; }";
    final TranslationUnit tu = ParseHelper.parse(program);
    new NullCheckTyper(tu) {
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertEquals(BasicType.BOOL, lookupType(memberLookupExpr));
      }
    };

  }

  @Test
  public void testSwizzleTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("void main() { vec2 v; v.xy = v.yx; }");
    Typer typer = new NullCheckTyper(tu);
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
    TranslationUnit tu = ParseHelper.parse("#version 440\n"
          + "void main() {"
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
    Typer typer = new NullCheckTyper(tu);
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
  public void testCommaTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 440\n"
        + "void main() {"
        + "int x;"
        + "int y;"
        + "x, y; }");
    Typer typer = new NullCheckTyper(tu);
    new StandardVisitor() {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        assertSame(binaryExpr.getOp(), BinOp.COMMA);
        assertEquals(BasicType.INT, typer.lookupType(binaryExpr));
      }
    }.visit(tu);
  }

  @Test
  public void testGlFrontFacingTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() { gl_FrontFacing; }");
    Typer typer = new NullCheckTyper(tu);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRONT_FACING)) {
          assertEquals(BasicType.BOOL, typer.lookupType(variableIdentifierExpr));
        }
      }
    }.visit(tu);

  }

  @Test
  public void testGlPositionTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() { gl_Position = vec4(0.0)"
        + "; }");
    Typer typer = new NullCheckTyper(tu);
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
    TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() { gl_PointSize = 1.0; }");
    Typer typer = new NullCheckTyper(tu);
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
  public void testGlPointCoordTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() { gl_PointCoord; }");
    Typer typer = new NullCheckTyper(tu);
    new StandardVisitor() {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_POINT_COORD)) {
          assertEquals(BasicType.VEC2, typer.lookupType(variableIdentifierExpr));
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
  public void testGlWorkGroupSizeTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_WorkGroupSize",
        OpenGlConstants.GL_WORK_GROUP_SIZE,
        BasicType.UVEC3,
        TypeQualifier.CONST);
  }

  @Test
  public void testGlWorkGroupIdTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_WorkGroupID",
        OpenGlConstants.GL_WORK_GROUP_ID,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGlLocalInvocationIdTyped() throws Exception {
    checkComputeShaderBuiltin(
        "gl_LocalInvocationID",
        OpenGlConstants.GL_LOCAL_INVOCATION_ID,
        BasicType.UVEC3,
        TypeQualifier.SHADER_INPUT);
  }

  @Test
  public void testGlGlobalInvocationIdTyped() throws Exception {
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
    new NullCheckTyper(tu) {
      @Override
      public void visitInitializer(Initializer initializer) {
        super.visitInitializer(initializer);
        assertSame(lookupType(initializer.getExpr()), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void testHexIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 0xA03B;"
        + "}");
    new NullCheckTyper(tu) {
      @Override
      public void visitInitializer(Initializer initializer) {
        super.visitInitializer(initializer);
        assertSame(lookupType(initializer.getExpr()), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void testOctalUnsignedIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 031u;"
        + "}");
    new NullCheckTyper(tu) {
      @Override
      public void visitInitializer(Initializer initializer) {
        super.visitInitializer(initializer);
        assertSame(lookupType(initializer.getExpr()), BasicType.UINT);
      }
    }.visit(tu);
  }

  @Test
  public void testHexUnsignedIntLiteralTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {"
        + "  int x = 0xA03Bu;"
        + "}");
    new NullCheckTyper(tu) {
      @Override
      public void visitInitializer(Initializer initializer) {
        super.visitInitializer(initializer);
        assertSame(lookupType(initializer.getExpr()), BasicType.UINT);
      }
    }.visit(tu);
  }

  @Test
  public void testEqualityAndInequalityVectorsMatrices() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {\n"
        + "  float v1a, v1b;\n"
        + "  vec2 v2a, v2b;\n"
        + "  vec3 v3a, v3b;\n"
        + "  vec4 v4a, v4b;\n"
        + "  int i1a, i1b;\n"
        + "  ivec2 i2a, i2b;\n"
        + "  ivec3 i3a, i3b;\n"
        + "  ivec4 i4a, i4b;\n"
        + "  uint u1a, u1b;\n"
        + "  uvec2 u2a, u2b;\n"
        + "  uvec3 u3a, u3b;\n"
        + "  uvec4 u4a, u4b;\n"
        + "  bool b1a, b1b;\n"
        + "  bvec2 b2a, b2b;\n"
        + "  bvec3 b3a, b3b;\n"
        + "  bvec4 b4a, b4b;\n"
        + "  mat2x2 m22a, m22b;\n"
        + "  mat2x3 m23a, m23b;\n"
        + "  mat2x4 m24a, m24b;\n"
        + "  mat3x2 m32a, m32b;\n"
        + "  mat3x3 m33a, m33b;\n"
        + "  mat3x4 m34a, m34b;\n"
        + "  mat4x2 m42a, m42b;\n"
        + "  mat4x3 m43a, m43b;\n"
        + "  mat4x2 m44a, m44b;\n"
        + "  v1a == v1b;\n"
        + "  v2a == v2b;\n"
        + "  v3a == v3b;\n"
        + "  v4a == v4b;\n"
        + "  v1a != v1b;\n"
        + "  v2a != v2b;\n"
        + "  v3a != v3b;\n"
        + "  v4a != v4b;\n"
        + "  u1a == u1b;\n"
        + "  u2a == u2b;\n"
        + "  u3a == u3b;\n"
        + "  u4a == u4b;\n"
        + "  u1a != u1b;\n"
        + "  u2a != u2b;\n"
        + "  u3a != u3b;\n"
        + "  u4a != u4b;\n"
        + "  i1a == i1b;\n"
        + "  i2a == i2b;\n"
        + "  i3a == i3b;\n"
        + "  i4a == i4b;\n"
        + "  i1a != i1b;\n"
        + "  i2a != i2b;\n"
        + "  i3a != i3b;\n"
        + "  i4a != i4b;\n"
        + "  b1a == b1b;\n"
        + "  b2a == b2b;\n"
        + "  b3a == b3b;\n"
        + "  b4a == b4b;\n"
        + "  b1a != b1b;\n"
        + "  b2a != b2b;\n"
        + "  b3a != b3b;\n"
        + "  b4a != b4b;\n"
        + "  m22a == m22b;\n"
        + "  m22a != m22b;\n"
        + "  m23a == m23b;\n"
        + "  m23a != m23b;\n"
        + "  m24a == m24b;\n"
        + "  m24a != m24b;\n"
        + "  m32a == m32b;\n"
        + "  m32a != m32b;\n"
        + "  m33a == m33b;\n"
        + "  m33a != m33b;\n"
        + "  m34a == m34b;\n"
        + "  m34a != m34b;\n"
        + "  m42a == m42b;\n"
        + "  m42a != m42b;\n"
        + "  m43a == m43b;\n"
        + "  m43a != m43b;\n"
        + "  m44a == m44b;\n"
        + "  m44a != m44b;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (binaryExpr.getOp() == BinOp.EQ || binaryExpr.getOp() == BinOp.NE) {
          assertSame(lookupType(binaryExpr), BasicType.BOOL);
        }
      }
    }.visit(tu);
  }

  @Test
  public void testVectorRelational() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "void main() {\n"
        + "  vec2 v2a, v2b;\n"
        + "  vec3 v3a, v3b;\n"
        + "  vec4 v4a, v4b;\n"
        + "  ivec2 i2a, i2b;\n"
        + "  ivec3 i3a, i3b;\n"
        + "  ivec4 i4a, i4b;\n"
        + "  uvec2 u2a, u2b;\n"
        + "  uvec3 u3a, u3b;\n"
        + "  uvec4 u4a, u4b;\n"
        + "  bvec2 b2a, b2b;\n"
        + "  bvec3 b3a, b3b;\n"
        + "  bvec4 b4a, b4b;\n"
        + "\n"
        + "  lessThan(v2a, v2b);\n"
        + "  lessThan(v3a, v3b);\n"
        + "  lessThan(v4a, v4b);\n"
        + "  lessThan(i2a, i2b);\n"
        + "  lessThan(i3a, i3b);\n"
        + "  lessThan(i4a, i4b);\n"
        + "  lessThan(u2a, u2b);\n"
        + "  lessThan(u3a, u3b);\n"
        + "  lessThan(u4a, u4b);\n"
        + "\n"
        + "  lessThanEqual(v2a, v2b);\n"
        + "  lessThanEqual(v3a, v3b);\n"
        + "  lessThanEqual(v4a, v4b);\n"
        + "  lessThanEqual(i2a, i2b);\n"
        + "  lessThanEqual(i3a, i3b);\n"
        + "  lessThanEqual(i4a, i4b);\n"
        + "  lessThanEqual(u2a, u2b);\n"
        + "  lessThanEqual(u3a, u3b);\n"
        + "  lessThanEqual(u4a, u4b);\n"
        + "\n"
        + "  greaterThan(v2a, v2b);\n"
        + "  greaterThan(v3a, v3b);\n"
        + "  greaterThan(v4a, v4b);\n"
        + "  greaterThan(i2a, i2b);\n"
        + "  greaterThan(i3a, i3b);\n"
        + "  greaterThan(i4a, i4b);\n"
        + "  greaterThan(u2a, u2b);\n"
        + "  greaterThan(u3a, u3b);\n"
        + "  greaterThan(u4a, u4b);\n"
        + "\n"
        + "  greaterThanEqual(v2a, v2b);\n"
        + "  greaterThanEqual(v3a, v3b);\n"
        + "  greaterThanEqual(v4a, v4b);\n"
        + "  greaterThanEqual(i2a, i2b);\n"
        + "  greaterThanEqual(i3a, i3b);\n"
        + "  greaterThanEqual(i4a, i4b);\n"
        + "  greaterThanEqual(u2a, u2b);\n"
        + "  greaterThanEqual(u3a, u3b);\n"
        + "  greaterThanEqual(u4a, u4b);\n"
        + "\n"
        + "  equal(v2a, v2b);\n"
        + "  equal(v3a, v3b);\n"
        + "  equal(v4a, v4b);\n"
        + "  equal(i2a, i2b);\n"
        + "  equal(i3a, i3b);\n"
        + "  equal(i4a, i4b);\n"
        + "  equal(u2a, u2b);\n"
        + "  equal(u3a, u3b);\n"
        + "  equal(u4a, u4b);\n"
        + "  equal(b2a, b2b);\n"
        + "  equal(b3a, b3b);\n"
        + "  equal(b4a, b4b);\n"
        + "\n"
        + "  notEqual(v2a, v2b);\n"
        + "  notEqual(v3a, v3b);\n"
        + "  notEqual(v4a, v4b);\n"
        + "  notEqual(i2a, i2b);\n"
        + "  notEqual(i3a, i3b);\n"
        + "  notEqual(i4a, i4b);\n"
        + "  notEqual(u2a, u2b);\n"
        + "  notEqual(u3a, u3b);\n"
        + "  notEqual(u4a, u4b);\n"
        + "  notEqual(b2a, b2b);\n"
        + "  notEqual(b3a, b3b);\n"
        + "  notEqual(b4a, b4b);\n"
        + "\n"
        + "  any(b2a);\n"
        + "  any(b3a);\n"
        + "  any(b4a);\n"
        + "\n"
        + "  all(b2a);\n"
        + "  all(b3a);\n"
        + "  all(b4a);\n"
        + "\n"
        + "  not(b2a);\n"
        + "  not(b3a);\n"
        + "  not(b4a);\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);

        // There must be a first argument type and it must be a vec type.
        BasicType firstArgType =
            (BasicType) lookupType(functionCallExpr.getArg(0)).getWithoutQualifiers();
        assertTrue(firstArgType.isVector());
        if (functionCallExpr.getNumArgs() > 1) {
          // If there is a second argument type it must match the first argument type.
          assertEquals(2, functionCallExpr.getNumArgs());
          assertEquals(firstArgType, lookupType(functionCallExpr.getArg(1)).getWithoutQualifiers());
        }
        if (functionCallExpr.getCallee().equals("any") || functionCallExpr.getCallee().equals(
            "all")) {
          // 'any' and 'all' return 'bool' in all cases.
          assertSame(BasicType.BOOL, lookupType(functionCallExpr));
        } else {
          // If the first argument is a vector of length n, the result type must be 'bvecn'.
          assertEquals(BasicType.makeVectorType(BasicType.BOOL, firstArgType.getNumElements()),
              lookupType(functionCallExpr));
        }
      }
    }.visit(tu);
  }

  @Test
  public void testConstArrayCorrectlyTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  const int A[2] = int[2](1, 2);\n"
        + "  A;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        super.visitVariableIdentifierExpr(variableIdentifierExpr);
        assertEquals("A", variableIdentifierExpr.getName());
        final Type type = lookupType(variableIdentifierExpr);
        assertTrue(type.hasQualifier(TypeQualifier.CONST));
        assertTrue(type.getWithoutQualifiers() instanceof ArrayType);
        final ArrayType withoutQualifiers = (ArrayType) type.getWithoutQualifiers();
        assertSame(withoutQualifiers.getBaseType(), BasicType.INT);
        try {
          assertEquals(2, withoutQualifiers.getArrayInfo().getConstantSize(0).intValue());
        } catch (UnsupportedLanguageFeatureException exception) {
          fail();
        }
      }
    };
  }

  @Test
  public void testArrayConstructorCorrectlyTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "void main() {\n"
        + "  int[2](1, 2);\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitArrayConstructorExpr(ArrayConstructorExpr arrayConstructorExpr) {
        super.visitArrayConstructorExpr(arrayConstructorExpr);
        final Type type = lookupType(arrayConstructorExpr);
        assertTrue(type instanceof ArrayType);
        assertSame(((ArrayType) type).getBaseType(), BasicType.INT);
        assertEquals(2, (int) ((ArrayType) type).getArrayInfo().getConstantSize(0));
      }
    };
  }

  @Test
  public void testStructAssignCorrectlyTyped() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "struct S { float x; } myS;\n"
        + "void main() {\n"
        + "  S anotherS;\n"
        + "  anotherS = myS;\n"
        + "  myS = anotherS;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        final Type type = lookupType(binaryExpr);
        assertTrue(type instanceof StructNameType || type instanceof StructDefinitionType);
        assertEquals("S", ((StructNameType) TyperHelper.maybeGetStructName(type)).getName());
      }
    };
  }

  @Test
  public void testPrototypeMatchingWithStructs() throws Exception {
    TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "struct S { float x; } myS;\n"
        + "void foo(S myS);"
        + "void main() {\n"
        + "  foo(myS);\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        assertSame(VoidType.VOID, lookupType(functionCallExpr).getWithoutQualifiers());
      }
    };
  }

  @Test
  public void testLengthTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 450\n"
        + "void main() {\n"
        + "  int A[5];\n"
        + "  vec4 v;\n"
        + "  mat4x4 m;\n"
        + "  A.length();\n"
        + "  v.length();\n"
        + "  m.length();\n"
        + "}\n");
    new NullCheckTyper(tu) {
      @Override
      public void visitLengthExpr(LengthExpr lengthExpr) {
        super.visitLengthExpr(lengthExpr);
        assertSame(lookupType(lengthExpr), BasicType.INT);
      }
    }.visit(tu);
  }

  @Test
  public void testLeftShiftTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 320 es\n"
        + "void main() {\n"
        + "  int a;\n"
        + "  uint b;\n"
        + "  ivec2 av;\n"
        + "  uvec2 bv;\n"
        + "\n"
        + "  int x0 = a << a;\n"
        + "  int x1 = a << b;\n"
        + "  uint x2 = b << a;\n"
        + "  uint x3 = b << b;\n"
        + "  ivec2 x4 = av << a;\n"
        + "  ivec2 x5 = av << b;\n"
        + "  ivec2 x6 = av << av;\n"
        + "  ivec2 x7 = av << bv;\n"
        + "  uvec2 x8 = bv << a;\n"
        + "  uvec2 x9 = bv << b;\n"
        + "  uvec2 x10 = bv << av;\n"
        + "  uvec2 x11 = bv << bv;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      private int counter = 0;
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (binaryExpr.getOp() == BinOp.SHL) {
          switch (counter) {
            case 0:
            case 1:
              assertSame(BasicType.INT, lookupType(binaryExpr));
              break;
            case 2:
            case 3:
              assertSame(BasicType.UINT, lookupType(binaryExpr));
              break;
            case 4:
            case 5:
            case 6:
            case 7:
              assertSame(BasicType.IVEC2, lookupType(binaryExpr));
              break;
            case 8:
            case 9:
            case 10:
            case 11:
              assertSame(BasicType.UVEC2, lookupType(binaryExpr));
              break;
            default:
              fail();
          }
          counter++;
        }
      }
    }.visit(tu);
  }

  @Test
  public void testRightShiftTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 320 es\n"
        + "void main() {\n"
        + "  int a;\n"
        + "  uint b;\n"
        + "  ivec2 av;\n"
        + "  uvec2 bv;\n"
        + "\n"
        + "  int x0 = a >> a;\n"
        + "  int x1 = a >> b;\n"
        + "  uint x2 = b >> a;\n"
        + "  uint x3 = b >> b;\n"
        + "  ivec2 x4 = av >> a;\n"
        + "  ivec2 x5 = av >> b;\n"
        + "  ivec2 x6 = av >> av;\n"
        + "  ivec2 x7 = av >> bv;\n"
        + "  uvec2 x8 = bv >> a;\n"
        + "  uvec2 x9 = bv >> b;\n"
        + "  uvec2 x10 = bv >> av;\n"
        + "  uvec2 x11 = bv >> bv;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      private int counter = 0;
      @Override
      public void visitBinaryExpr(BinaryExpr binaryExpr) {
        super.visitBinaryExpr(binaryExpr);
        if (binaryExpr.getOp() == BinOp.SHR) {
          switch (counter) {
            case 0:
            case 1:
              assertSame(BasicType.INT, lookupType(binaryExpr));
              break;
            case 2:
            case 3:
              assertSame(BasicType.UINT, lookupType(binaryExpr));
              break;
            case 4:
            case 5:
            case 6:
            case 7:
              assertSame(BasicType.IVEC2, lookupType(binaryExpr));
              break;
            case 8:
            case 9:
            case 10:
            case 11:
              assertSame(BasicType.UVEC2, lookupType(binaryExpr));
              break;
            default:
              fail();
          }
          counter++;
        }
      }
    }.visit(tu);
  }

  @Test
  public void testScalarSwizzleTyped() throws Exception {
    final TranslationUnit tu = ParseHelper.parse("#version 420\n"
        + "void main() {\n"
        + "  float f;\n"
        + "  vec3 fv;\n"
        + "  int i;\n"
        + "  ivec3 iv;\n"
        + "  uint u;\n"
        + "  uvec3 uv;\n"
        + "  bool b;\n"
        + "  bvec3 bv;\n"
        + "  f.x;\n"
        + "  f.r;\n"
        + "  f.s;\n"
        + "  fv.x.x;\n"
        + "  fv.g.g;\n"
        + "  fv.t.t;\n"
        + "  i.x;\n"
        + "  i.r;\n"
        + "  i.s;\n"
        + "  iv.x.x;\n"
        + "  iv.g.g;\n"
        + "  iv.t.t;\n"
        + "  u.x;\n"
        + "  u.r;\n"
        + "  u.s;\n"
        + "  uv.x.x;\n"
        + "  uv.g.g;\n"
        + "  uv.t.t;\n"
        + "  b.x;\n"
        + "  b.r;\n"
        + "  b.s;\n"
        + "  bv.x.x;\n"
        + "  bv.g.g;\n"
        + "  bv.t.t;\n"
        + "}\n");
    new NullCheckTyper(tu) {
      private int counter = 0;
      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        assertTrue(Arrays.asList("x", "r", "g", "s", "t").contains(memberLookupExpr.getMember()));
        final BasicType type =
            (BasicType) lookupType(memberLookupExpr.getStructure()).getWithoutQualifiers();
        assertSame(type.getElementType(), lookupType(memberLookupExpr));
      }
    }.visit(tu);
  }

  private void checkComputeShaderBuiltin(String builtin, String builtinConstant, BasicType baseType,
      TypeQualifier qualifier) throws IOException, ParseTimeoutException, InterruptedException,
      GlslParserException {
    TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "void main() { " + builtin + "; }");
    Typer typer = new NullCheckTyper(tu);
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

  static class NullCheckTyper extends Typer {

    NullCheckTyper(TranslationUnit tu) {
      super(tu);
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
  public void testBuiltinsEssl320() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_320;
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
    for (ShaderKind shaderKind : ShaderKind.values()) {
      if (shaderKind == ShaderKind.COMPUTE && !shadingLanguageVersion.supportedComputeShaders()) {
        // Compute shaders are not supported for older GLSL versions.
        continue;
      }
      final File tempFile = temporaryFolder.newFile("shader." + shaderKind.getFileExtension());
      FileUtils.writeStringToFile(
          tempFile,
          makeBuiltinsProgram(shadingLanguageVersion, shaderKind).toString(),
          StandardCharsets.UTF_8);
      final ExecResult result = ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, tempFile);
      assertEquals(0, result.res);
    }
  }

  private StringBuilder makeBuiltinsProgram(ShadingLanguageVersion shadingLanguageVersion,
                                            ShaderKind shaderKind) {
    StringBuilder result = new StringBuilder();
    result.append("#version " + shadingLanguageVersion.getVersionString() + "\n");
    result.append("#ifdef GL_ES\n");
    result.append("precision highp float;\n");
    result.append("precision highp int;\n");
    if (shadingLanguageVersion.supportedTexture()) {
      result.append("precision highp " + SamplerType.SAMPLER2D + ";\n");
      result.append("precision highp " + SamplerType.ISAMPLER2D + ";\n");
      result.append("precision highp " + SamplerType.USAMPLER2D + ";\n");
      result.append("precision highp " + SamplerType.SAMPLER3D + ";\n");
      result.append("precision highp " + SamplerType.ISAMPLER3D + ";\n");
      result.append("precision highp " + SamplerType.USAMPLER3D + ";\n");
      result.append("precision highp " + SamplerType.SAMPLERCUBE + ";\n");
      result.append("precision highp " + SamplerType.ISAMPLERCUBE + ";\n");
      result.append("precision highp " + SamplerType.USAMPLERCUBE + ";\n");
      result.append("precision highp " + SamplerType.SAMPLER2DSHADOW + ";\n");
      result.append("precision highp " + SamplerType.SAMPLERCUBESHADOW + ";\n");
      result.append("precision highp " + SamplerType.SAMPLER2DARRAY + ";\n");
      result.append("precision highp " + SamplerType.ISAMPLER2DARRAY + ";\n");
      result.append("precision highp " + SamplerType.USAMPLER2DARRAY + ";\n");
      result.append("precision highp " + SamplerType.SAMPLER2DARRAYSHADOW + ";\n");
    }
    result.append("#endif\n");
    int counter = 0;
    for (String name :
        TyperHelper.getBuiltins(shadingLanguageVersion, false, shaderKind).keySet()) {
      for (FunctionPrototype fp : TyperHelper.getBuiltins(shadingLanguageVersion, false, shaderKind)
          .get(name)) {
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
        result.append("  ");
        if (fp.getReturnType() != VoidType.VOID) {
          result.append("return ");
        }
        result.append(fp.getName() + "(");
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
