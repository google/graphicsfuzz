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

package com.graphicsfuzz.generator.semanticschanging;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.mutateapi.Mutation;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class InterchangeExprMutationFinderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testValidInterchanges() throws Exception {
    final IRandom generator = new RandomWrapper(0);
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    for (File shaderJobFile : fileOperations.listShaderJobFiles(
        Paths.get(ToolPaths.getShadersDirectory(), "samples", "100").toFile(),
        ((dir, name) -> true))) {
      final ShaderJob shaderJob = fileOperations.readShaderJobFile(shaderJobFile);
      new InterchangeExprMutationFinder(shaderJob.getFragmentShader().get(), generator)
          .findMutations().forEach(Mutation::apply);
    }
  }

}
