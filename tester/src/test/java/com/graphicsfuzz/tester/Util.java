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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ImageUtil;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

public final class Util {

  private Util() {
    // Utility class
  }

  static File validateAndGetImage(
      File shaderJobFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {
    assertTrue(fileOps.areShadersValid(shaderJobFile, false));
    assertTrue(fileOps.areShadersValidShaderTranslator(shaderJobFile, false));
    return getImage(shaderJobFile, temporaryFolder, fileOps);
  }

  static File validateAndGetImage(
      ShaderJob shaderJob,
      String shaderJobFilename,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {
    final File shaderJobFile = new File(
        temporaryFolder.getRoot(),
        shaderJobFilename);
    fileOps.writeShaderJobFile(shaderJob, shaderJobFile);
    return validateAndGetImage(shaderJobFile, temporaryFolder, fileOps);
  }

  static File getImage(
      File shaderJobFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps) throws IOException, InterruptedException {
    File imageFile = temporaryFolder.newFile();
    ExecResult res =
        ToolHelper.runSwiftshaderOnShader(RedirectType.TO_LOG,
            fileOps.getUnderlyingShaderFile(shaderJobFile, ShaderKind.FRAGMENT),
            imageFile,
            false,
            32,
            32);

    assertEquals(0, res.res);
    return imageFile;
  }

  static void assertImagesSimilar(File first, File second) throws FileNotFoundException {
    // TODO: This has been made very generous, based on Swiftshader producing visually identical
    // images with fairly high associated histogram distances.  If we find that bugs are slipping
    // through we should revise this.
    assertTrue(ImageUtil.compareHistograms(first, second) < 500.0);
  }

  static File[] getReferenceShaderJobFiles100es(ShaderJobFileOperations fileOps)
      throws IOException {

    return fileOps.listShaderJobFiles(
        Paths
            .get(ToolPaths.getShadersDirectory(), "testing", "swiftshader", "100")
            .toFile());
  }

  static File[] getReferenceShaderJobFiles300es(ShaderJobFileOperations fileOps)
      throws IOException {

    return fileOps.listShaderJobFiles(
        Paths
            .get(ToolPaths.getShadersDirectory(), "testing", "swiftshader", "300es")
            .toFile());
  }

  static File getDonorsFolder() {
    return Paths.get(ToolPaths.getShadersDirectory(),
        "testing", "swiftshader", "donors").toFile();
  }

}
