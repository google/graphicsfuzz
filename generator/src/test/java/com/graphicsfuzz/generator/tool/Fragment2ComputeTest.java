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

package com.graphicsfuzz.generator.tool;

import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Fragment2ComputeTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testTranslationToCompute() throws Exception {
    // Check that we can turn fragment shaders into valid compute shaders.
    final File referencesDir = Paths.get(ToolPaths.getShadersDirectory(), "samples",
        "320es").toFile();

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    String[] blocklist = {"trigonometric_strip", "mergesort_mosaic"};

    for (File reference :
        referencesDir.listFiles((dir, name) -> name.endsWith(".json")
            && Arrays.stream(blocklist).noneMatch(s -> name.contains(s)))) {
      File outputShaderJob = temporaryFolder.newFile(reference.getName());
      Fragment2Compute.mainHelper(reference.getAbsolutePath(), outputShaderJob.getAbsolutePath());
      assertTrue(fileOps.getUnderlyingShaderFile(outputShaderJob, ShaderKind.COMPUTE)
          .isFile());
      assertTrue(fileOps.areShadersValid(outputShaderJob, false));
    }

  }


}
