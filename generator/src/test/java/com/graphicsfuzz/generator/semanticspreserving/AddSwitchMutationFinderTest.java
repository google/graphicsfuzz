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

import static org.junit.Assert.*;

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

    // Check that splitting loops many times, with no memory in-between (other than
    // the AST itself), leads to valid shaders.
    for (int i = 0; i < limit; i++) {
      final List<AddSwitchMutation> mutations =
          new AddSwitchMutationFinder(tu,
              new RandomWrapper(i),
              GenerationParams.normal(tu.getShaderKind(), false))
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

}
