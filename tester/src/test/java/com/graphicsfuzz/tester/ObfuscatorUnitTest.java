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

package com.graphicsfuzz.tester;

import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.Obfuscator;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ObfuscatorUnitTest {

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testObfuscate() throws Exception {
    final IRandom generator = new RandomWrapper(0);
    for (File originalShaderJobFile : Util.getReferenceShaderJobFiles100es(fileOps)) {
      final File originalImage =
          Util.validateAndGetImage(originalShaderJobFile, temporaryFolder, fileOps);
      final ShaderJob shaderJob = fileOps.readShaderJobFile(originalShaderJobFile);
      final ShaderJob obfuscated = Obfuscator.obfuscate(shaderJob, generator);
      final File obfuscatedImage =
          Util.validateAndGetImage(
              obfuscated,
              originalShaderJobFile.getName() + ".obfuscated.json",
              temporaryFolder,
              fileOps);
      assertTrue(FileUtils.contentEquals(originalImage, obfuscatedImage));
    }
  }

}
