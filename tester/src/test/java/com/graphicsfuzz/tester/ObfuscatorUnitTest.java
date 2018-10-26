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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.Obfuscator;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.io.File;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertTrue;

public class ObfuscatorUnitTest {

  // TODO: Use ShaderJobFileOperations everywhere.
  private final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testObfuscate() throws Exception {
    final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion.ESSL_100;
    final IRandom generator = new RandomWrapper(0);
    for (File originalShaderJobFile : Util.getReferenceShaderJobFiles()) {
      final File originalImage =
          Util.renderShader(
              shadingLanguageVersion, originalShaderJobFile, temporaryFolder, fileOps);
      final ShaderJob shaderJob = fileOps.readShaderJobFile(originalShaderJobFile);
      for (TranslationUnit tu : shaderJob.getShaders()) {
        assert !tu.hasShadingLanguageVersion();
        tu.setShadingLanguageVersion(ShadingLanguageVersion.ESSL_100);
      }
      final ShaderJob obfuscated = Obfuscator.obfuscate(shaderJob, generator);
      final File obfuscatedImage =
          Util.validateAndGetImage(
              obfuscated,
              originalShaderJobFile.getName() + ".obfuscated.frag",
              temporaryFolder,
              fileOps);
      assertTrue(FileUtils.contentEquals(originalImage, obfuscatedImage));
    }
  }

}
