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

package com.graphicsfuzz.generator.semanticspreserving;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.CannedRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.mutateapi.Expr2ExprMutation;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IdentityMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testNumMutationPoints() throws Exception {
    final String program =
        "#version 450\n"
            + "void main() {\n"
            + "  int x = 0;\n"
            + "  int j = 0;\n"
            + "  for (int x = 0; x < 100; x++) {\n"
            + "    j += x;\n"
            + "  }\n"
            + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(
        tu,
        new CannedRandom(),
        GenerationParams.normal(ShaderKind.FRAGMENT, true));
    final List<Expr2ExprMutation> mutations = identityMutationFinder.findMutations();
    assertEquals(9, mutations.size());
  }

  @Test
  public void testNumMutationPoints100WebGl() throws Exception {
    final String program =
        "#version 100\n"
            + "precision mediump float;\n"
            + "void main() {\n"
            + "  int x = 0 /*can be mutated*/;\n"
            + "  int j = 0 /*can be mutated*/;\n"
            + "  for (/*none of this can be mutated*/ int x = 0; x < 100; x++) {\n"
            + "    j /*j cannot be mutated*/ += x /*x can be mutated*/ /*the whole of j += x can "
            + "be mutated */"
            + ";\n"
            + "  }\n"
            + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(
        tu,
        new CannedRandom(),
        GenerationParams.normal(ShaderKind.FRAGMENT, false));
    final List<Expr2ExprMutation> mutations = identityMutationFinder.findMutations();
    // The loop guard is untouchable as this is GLSL 100.
    assertEquals(4, mutations.size());
  }

  @Test
  public void testNoMutationPoints100WebGl() throws Exception {
    final String program =
        "void main() {\n"
            + "  int j = 0;"
            + "  for (int x = 0; x < 100; x++) {\n"
            + "    j = j + 1;"
            + "  }\n"
            + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);
    final IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(
        tu,
        new CannedRandom(),
        GenerationParams.normal(ShaderKind.FRAGMENT, true));
    final List<Expr2ExprMutation> mutations = identityMutationFinder.findMutations();
    // Loop guard is untouchable as this is GLSL 100.
    // LHS of "j = j + 1" is also untouchable.
    assertEquals(5, mutations.size());
  }

  @Test
  public void testNumMutationPointsSwitch() throws Exception {
    final String program =
        "#version 310 es\n"
            + "precision mediump float;\n"
            + "void main() {\n"
            + "  switch(0 /*can be mutated*/) {"
            + "    case 1 /*cannot be mutated*/:"
            + "    case 2 /*cannot be mutated*/:"
            + "    default:"
            + "      1 /*can be mutated*/;"
            + "  }"
            + "}";
    final TranslationUnit tu = ParseHelper.parse(program);
    final IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(
        tu,
        new CannedRandom(),
        GenerationParams.normal(ShaderKind.FRAGMENT, false));
    final List<Expr2ExprMutation> mutations = identityMutationFinder.findMutations();
    assertEquals(2, mutations.size());
  }

  @Test
  public void testAssignLhsInParentheses() throws Exception {
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  (((((((x))))))) = 2;\n"
        + "}\n";
    checkValidAfterExpressionIdentities(program);
  }

  @Test
  public void testPlusEqualLhsInParentheses() throws Exception {
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  (((((((x))))))) += 2;\n"
        + "}\n";
    checkValidAfterExpressionIdentities(program);
  }

  @Test
  public void testPreIncrementInParentheses() throws Exception {
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  ++(((((((x)))))));\n"
        + "}\n";
    checkValidAfterExpressionIdentities(program);
  }

  @Test
  public void testPostDecrementInParentheses() throws Exception {
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  (((((((x)))))))--;\n"
        + "}\n";
    checkValidAfterExpressionIdentities(program);
  }

  @Test
  public void testFunctionOutArgsInParentheses() throws Exception {
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void foo(out int a, inout int b) {"
        + "  a = 3;"
        + "  b += 4;"
        + "}"
        + "void main() {\n"
        + "  int x = 0;\n"
        + "  int y = 0;\n"
        + "  foo((((x))), (((y))));\n"
        + "}\n";
    checkValidAfterExpressionIdentities(program);
  }

  private void checkValidAfterExpressionIdentities(String program) throws Exception {
    final TranslationUnit tu = ParseHelper.parse(program);
    final IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(
        tu,
        new RandomWrapper(0),
        GenerationParams.normal(ShaderKind.FRAGMENT, false));
    final List<Expr2ExprMutation> mutations = identityMutationFinder.findMutations();
    mutations.forEach(Mutation::apply);
    assertTrue(shaderIsValid(tu));
  }

  private boolean shaderIsValid(TranslationUnit tu) throws Exception {
    final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo("{}"), tu);
    final File shaderJobFile = temporaryFolder.newFile("shader.json");
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    fileOperations.writeShaderJobFile(shaderJob, shaderJobFile);
    return fileOperations.areShadersValid(shaderJobFile, false);
  }

  @Test
  public void testMutateTrue() throws Exception {
    // Regression test for bug where a mutation to one occurrence of 'true' could affect another.
    final TranslationUnit tu = ParseHelper.parse("#version 310 es\n"
        + "precision highp float;\n"
        + "void foo() {"
        + "  true;"
        + "}"
        + "void main() {"
        + "  true;"
        + "}");
    final List<Expr2ExprMutation> mutations =
        new IdentityMutationFinder(tu, new RandomWrapper(0),
            GenerationParams.normal(ShaderKind.FRAGMENT, false))
            .findMutations();
    assertEquals(2, mutations.size());
    mutations.get(0).apply();
    assertEquals("void main()\n{\n true;\n}\n",
        PrettyPrinterVisitor.prettyPrintAsString(tu.getMainFunction()));

  }

  @Test
  public void testMatrixIdentityOk() throws Exception {
    // This test aims to check that when we replace m with "m / e" or "m * e", "e" is the
    // identity matrix.
    final String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {"
        + "  mat2 m;"
        + "  m;"
        + "}";
    for (int i = 0; i < 20; i++) {
      final TranslationUnit tu = ParseHelper.parse(program);
      IdentityMutationFinder identityMutationFinder = new IdentityMutationFinder(tu,
          new RandomWrapper(i), GenerationParams.small(ShaderKind.FRAGMENT, false));
      identityMutationFinder.findMutations().forEach(Expr2ExprMutation::apply);
      new StandardVisitor() {
        @Override
        public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
          super.visitFunctionCallExpr(functionCallExpr);
          if (!functionCallExpr.getCallee().equals(Constants.GLF_IDENTITY)) {
            return;
          }
          if (!(functionCallExpr.getArgs().get(0) instanceof VariableIdentifierExpr)) {
            return;
          }
          if (!(((VariableIdentifierExpr) functionCallExpr.getArgs().get(0)).getName()
              .equals("m"))) {
            return;
          }
          if (!(functionCallExpr.getArgs().get(1) instanceof BinaryExpr)) {
            return;
          }
          final BinaryExpr binaryExpr = (BinaryExpr) functionCallExpr.getArgs().get(1);
          if (!Arrays.asList(BinOp.DIV, BinOp.MUL).contains(binaryExpr.getOp())) {
            return;
          }
          if (!(binaryExpr.getRhs() instanceof TypeConstructorExpr)) {
            return;
          }
          final TypeConstructorExpr typeConstructorExpr = (TypeConstructorExpr) binaryExpr.getRhs();
          if (!typeConstructorExpr.getTypename().equals(BasicType.MAT2X2.toString())) {
            return;
          }
          if (binaryExpr.getOp() == BinOp.MUL) {
            // In the case of MUL we should be multiplying by the identity matrix.
            // The identity matrix should only have one arg, and if it is a literal it should be
            // 1.0.
            assertTrue(typeConstructorExpr.getNumArgs() == 1);
            if (typeConstructorExpr.getArg(0) instanceof FloatConstantExpr) {
              assertEquals("1.0", ((FloatConstantExpr) typeConstructorExpr.getArg(0)).getValue());
            }
          } else {
            // In the case of DIV we should be multiplying by a matrix of all 1.0s.
            assertEquals(binaryExpr.getOp(), BinOp.DIV);
            assertTrue(typeConstructorExpr.getNumArgs() == 4);
            for (int i = 0; i < 4; i++) {
              if (typeConstructorExpr.getArg(i) instanceof FloatConstantExpr) {
                assertEquals("1.0", ((FloatConstantExpr) typeConstructorExpr.getArg(i)).getValue());
              }
            }
          }
        }
      }.visit(tu);
    }
  }

  @Test
  public void testConstMutations() throws Exception {
    // This checks that constant initializers are not mutated in a way that makes them non-constant.
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "uniform vec2 injectionSwitch;\n"
        + "const int a = 1;\n"
        + "const int b = 2;\n"
        + "const float c = 3.0;\n"
        + "const float d = 4.0;\n"
        + "void main() {"
        + "  const int x = 2;\n"
        + "  const int y = 3;\n"
        + "  const float f = 4.0;\n"
        + "  const float g = 5.0;\n"
        + "}";
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    for (int i = 0; i < 20; i++) {
      final TranslationUnit tu = ParseHelper.parse(program);
      new IdentityMutationFinder(tu,
          new RandomWrapper(i), GenerationParams.normal(ShaderKind.FRAGMENT, true))
          .findMutations()
          .forEach(Expr2ExprMutation::apply);
      final File shaderJobFile = temporaryFolder.newFile("shader" + i + ".json");
      final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(),
          tu);
      fileOperations.writeShaderJobFile(shaderJob, shaderJobFile);
      // Check that there is no invalidity due to mis-use of constants (in which case an exception
      // will be thrown, but we assert the result of 'areShadersValid' is true just to be sure.
      assertTrue(fileOperations.areShadersValid(shaderJobFile, true));
    }
  }

  @Test
  public void testMutationsWithCommaOperator() throws Exception {
    // This checks that the _GLF_IDENTITY macro is applied appropriately in the presence of the
    // comma operator.
    final String program = "#version 310 es\n"
        + "precision highp float;\n"
        + "void main() {"
        + "  int a, b, c, d, e;\n"
        + "  a, b, c, d, e, a, b, c, d, e;\n"
        + "}";
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    for (int i = 0; i < 20; i++) {
      final TranslationUnit tu = ParseHelper.parse(program);
      new IdentityMutationFinder(tu,
          new RandomWrapper(i), GenerationParams.normal(ShaderKind.FRAGMENT, false))
          .findMutations()
          .forEach(Expr2ExprMutation::apply);
      final File shaderJobFile = temporaryFolder.newFile("shader" + i + ".json");
      final ShaderJob shaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(),
          tu);
      fileOperations.writeShaderJobFile(shaderJob, shaderJobFile);
      // Check that there is no invalidity due to mis-use of constants (in which case an exception
      // will be thrown, but we assert the result of 'areShadersValid' is true just to be sure.
      assertTrue(fileOperations.areShadersValid(shaderJobFile, true));
    }
  }

}
