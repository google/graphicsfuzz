/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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

import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AddSwitchMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testApplyRepeatedly() throws Exception {
    // Checks that adding switch statements several times does not lead to an invalid shader.
    final int limit = 8;
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    StringBuilder program = new StringBuilder("#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  float f = 2.0;\n"
        + "  int x = 3;\n"
        + "  int g = 0;\n");
    for (int i = 0; i < limit; i++) {
      program.append("  { g++; }\n");
    }
    program.append("}\n");
    final TranslationUnit tu = ParseHelper.parse(program.toString());

    // Check that adding switches many times leads to valid shaders.
    for (int i = 0; i < limit; i++) {
      final List<AddSwitchMutation> mutations =
          new AddSwitchMutationFinder(tu,
              new RandomWrapper(i),
              GenerationParams.normal(tu.getShaderKind(), false, false))
              .findMutations();
      mutations.get(0).apply();
      if (mutations.size() > 1) {
        mutations.get(mutations.size() - 1).apply();
      }
      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

  @Test
  public void testNameShadowingInForLoop() throws Exception {
    // Guards against problems related to a variable declared in a 'for' shadowing an outer variable
    // when the 'for' loop is switchified.
    final int limit = 10;
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  float f = 2.0;\n"
        + "  for (int f = 0; f < 100; f++) {\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);

    for (int i = 0; i < limit; i++) {
      final List<AddSwitchMutation> mutations =
          new AddSwitchMutationFinder(tu,
              new RandomWrapper(i),
              GenerationParams.normal(tu.getShaderKind(), false, false))
              .findMutations();
      for (AddSwitchMutation mutation : mutations) {
        mutation.apply();
      }
      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

  @Test
  public void testNameShadowingInBlock() throws Exception {
    // Guards against problems related to a variable declared in a block shadowing an outer variable
    // when the block is switchified.
    final int limit = 10;
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    String program = "#version 300 es\n"
        + "precision highp float;\n"
        + "void main() {\n"
        + "  float a;\n"
        + "  float b;\n"
        + "  float c;\n"
        + "  float d;\n"
        + "  float e;\n"
        + "  {\n"
        + "    vec4 a;\n"
        + "    vec4 b;\n"
        + "    vec4 c;\n"
        + "    vec4 d;\n"
        + "    vec4 e;\n"
        + "  }\n"
        + "}\n";
    final TranslationUnit tu = ParseHelper.parse(program);

    final GenerationParams generationParams = GenerationParams.normal(tu.getShaderKind(), false,
        false);
    generationParams.setMaxInjectedSwitchCasesAfterOriginalCode(10);
    for (int i = 0; i < limit; i++) {
      final List<AddSwitchMutation> mutations =
          new AddSwitchMutationFinder(tu,
              new RandomWrapper(i),
              generationParams)
              .findMutations();
      for (AddSwitchMutation mutation : mutations) {
        mutation.apply();
      }
      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

}
