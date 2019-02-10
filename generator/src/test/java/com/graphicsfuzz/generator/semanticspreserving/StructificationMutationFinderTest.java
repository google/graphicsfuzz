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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.generator.util.GenerationParams;
import java.io.File;
import java.util.Optional;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class StructificationMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Ignore // TODO(243)
  @Test
  public void testApplyRepeatedly() throws Exception {
    // Checks that applying structification a few times does not lead to an invalid shader.
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    final TranslationUnit tu = ParseHelper.parse("#version 300 es\n"
        + "precision highp float;"
        + "void main() {"
        + "  int x;"
        + "  int y;"
        + "  int z;"
        + "  x = 2;"
        + "  y = 3;"
        + "  z = 4;"
        + "}");

    // Check that structifying twice, with no memory in-between (other than the AST itself),
    // leads to valid shaders.
    for (int i = 0; i < 2; i++) {
      new StructificationMutationFinder(tu, new IdGenerator(), new RandomWrapper(0),
          GenerationParams.normal(tu.getShaderKind(), true))
          .findMutations()
          .get(0).apply();

      final File shaderJobFile = temporaryFolder.newFile("shaderjob" + i + ".json");
      fileOperations.writeShaderJobFile(new GlslShaderJob(Optional.empty(),
          new PipelineInfo("{}"),
          tu), shaderJobFile);
      assertTrue(fileOperations.areShadersValid(shaderJobFile, false));
    }
  }

}
