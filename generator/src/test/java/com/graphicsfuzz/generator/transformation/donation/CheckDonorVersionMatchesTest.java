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

package com.graphicsfuzz.generator.transformation.donation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.transformation.DonateLiveCodeTransformation;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import java.io.File;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CheckDonorVersionMatchesTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testVersionMismatch() throws Exception {
    final File donors = temporaryFolder.newFolder();
    final File exampleDonor = new File(donors, "donor.json");
    final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
    final ShaderJob es100ShaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse("#version 100\nvec4 foo(bool t) {\nif (t) "
        + "return vec4(1,0,1,1);\nreturn vec4(0,1,1,1);\n}\nvoid main() "
        + "{\ngl_FragColor = foo(true);\n "
        + "}\n"));
    fileOperations.writeShaderJobFile(es100ShaderJob, exampleDonor);

    final ShaderJob es310ShaderJob = new GlslShaderJob(Optional.empty(),
        new PipelineInfo(), ParseHelper.parse("#version 310 es\nout vec4 pix;\nvoid main() { "
        + "pix = vec4(1,0,0,1);}\n"));
    final File reference = temporaryFolder.newFile("reference.json");
    fileOperations.writeShaderJobFile(es310ShaderJob, reference);

    final GenerationParams normalGenerationParams = GenerationParams.normal(ShaderKind.FRAGMENT,
        false,
        false);
    final DonateLiveCodeTransformation transformation = new DonateLiveCodeTransformation(
        TransformationProbabilities.ALWAYS::donateLiveCodeAtStmt,
        donors,
        normalGenerationParams,
        false);

    TranslationUnit tu = es310ShaderJob.getFragmentShader().get();
    try {
      transformation.apply(tu,
          TransformationProbabilities.ALWAYS,
          new RandomWrapper(0), normalGenerationParams);
      fail("An exception should have been thrown");
    } catch (RuntimeException runtimeException) {
      assertTrue(runtimeException.getMessage().startsWith("Incompatible versions"));
    }
  }

}
