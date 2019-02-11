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

import com.graphicsfuzz.common.ast.TranslationUnit;
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
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    // The mutations are:
    // - LHS and RHS of "x < 100"
    // - RHS of "j += x"
    // All others are currently disabled, either due to having a const context, being l-values,
    // or not being the children of expressions.
    // In due course it would be good to me more general.
    assertEquals(3, mutations.size());
  }

  @Test
  public void testNumMutationPoints100WebGL() throws Exception {
    final String program =
        "void main() {\n"
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
    // Only a single mutation: the loop guard is untouchable as this is GLSL 100.
    assertEquals(1, mutations.size());
  }

  @Test
  public void testNoMutationPoints100WebGL() throws Exception {
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
    // Two mutations: LHS and RHS of "j + 1", and RHS of "j = j + 1".
    // Loop guard is untouchable as this is GLSL 100.
    assertEquals(3, mutations.size());
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

  public void checkValidAfterExpressionIdentities(String program) throws Exception {
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

}
