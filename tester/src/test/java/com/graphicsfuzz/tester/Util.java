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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.FileHelper;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.ImageUtil;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class Util {

  private Util() {
    // Utility class
  }

  static File[] getReferenceShaderJobFiles() {
    return new File(TestShadersDirectory.getTestShadersDirectory(), "references")
        .listFiles((dir, name) -> name.endsWith(".json"));
  }

  static File renderShader(ShadingLanguageVersion shadingLanguageVersion,
                           File originalShader,
                           TemporaryFolder temporaryFolder, ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException, ParseTimeoutException, GlslParserException {
    final ShaderJob shaderJob = fileOps.readShaderJobFile(originalShader);

    // TODO: having to plug in the version here feels like a hack.  But this is due to the
    // version not being present in the shaders in the repo, which means that the can be
    // used for testing with multiple future versions.  There is a trade-off here to be revisited.
    for (TranslationUnit tu : shaderJob.getShaders()) {
      assert !tu.hasShadingLanguageVersion();
      tu.setShadingLanguageVersion(shadingLanguageVersion);
    }

    return validateAndGetImage(
            shaderJob,
            originalShader.getName() + ".reference.frag",
            temporaryFolder,
            fileOps);
  }

  static File validateAndGetImage(
      File shaderFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {

    validate(shaderFile);
    return getImage(shaderFile, temporaryFolder, fileOps);
  }

  static File getImage(
      File shaderFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps) throws IOException, InterruptedException {

    final Optional<String> shaderTranslatorArg = getShaderTranslatorArg(shaderFile, fileOps);
    if (shaderTranslatorArg.isPresent()) {
      final ExecResult shaderTranslatorResult = ToolHelper
            .runShaderTranslatorOnShader(RedirectType.TO_LOG, shaderFile,
                  shaderTranslatorArg.get());
      assertEquals(0, shaderTranslatorResult.res);
    }
    return getImageUsingSwiftshader(shaderFile, temporaryFolder);
  }

  static void validate(File shaderFile) throws IOException, InterruptedException {
    final ExecResult validatorResult = ToolHelper
          .runValidatorOnShader(RedirectType.TO_LOG, shaderFile);
    assertEquals(validatorResult.res, 0);
  }

  private static Optional<String> getShaderTranslatorArg(
      File shaderFile,
      ShaderJobFileOperations fileOps) throws IOException {
    Optional<String> shaderTranslatorArgs;

    // Using fileOps here, even though the rest of the code does not use it yet.
    Assert.assertTrue(shaderFile.getName().endsWith(".frag"));
    final File shaderJobFile = FileHelper.replaceExtension(shaderFile, ".json");

    final ShadingLanguageVersion shadingLanguageVersionFromShader =
        ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
            fileOps.getFirstTwoLinesOfShader(shaderJobFile, ShaderKind.FRAGMENT));
    // TODO: Use fileOps.

    if (shadingLanguageVersionFromShader == ShadingLanguageVersion.ESSL_300
        || shadingLanguageVersionFromShader == ShadingLanguageVersion.WEBGL2_SL) {
      shaderTranslatorArgs = Optional.of("-s=w2");
    } else if(shadingLanguageVersionFromShader == ShadingLanguageVersion.WEBGL_SL) {
      shaderTranslatorArgs = Optional.of("-s=w");
    } else {
      shaderTranslatorArgs = Optional.empty();
    }
    return shaderTranslatorArgs;
  }

  static File validateAndGetImage(
      ShaderJob shaderJob,
      String fileName,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {

    final File shaderJobFileOutput = new File(
        temporaryFolder.getRoot(),
        FilenameUtils.removeExtension(fileName) + ".json");

    fileOps.writeShaderJobFile(shaderJob, shaderJobFileOutput);

    // TODO: Use fileOps more.

    final File tempFile = FileHelper.replaceExtension(shaderJobFileOutput, ".frag");

    return validateAndGetImage(tempFile, temporaryFolder, fileOps);
  }

  static File getImageUsingSwiftshader(File shaderFile, TemporaryFolder temporaryFolder) throws IOException, InterruptedException {
    File imageFile = temporaryFolder.newFile();
    ExecResult res =
        ToolHelper.runSwiftshaderOnShader(RedirectType.TO_LOG,
            shaderFile,
            imageFile,
            false,
            32,
            32);

    assertEquals(0, res.res);
    return imageFile;
  }

  static File createDonorsFolder(TemporaryFolder temporaryFolder) throws IOException {
    final File[] originalShaderFiles = Util.getReferenceShaderJobFiles();
    final File donorsFolder = temporaryFolder.newFolder();
    for (File originalShader : originalShaderFiles) {
      FileUtils.copyFile(originalShader,
          Paths.get(donorsFolder.getAbsolutePath(), originalShader.getName()).toFile());
    }
    return donorsFolder;
  }

  static void assertImagesSimilar(File first, File second) throws FileNotFoundException {
    // TODO: This has been made very generous, based on Swiftshader producing visually identical
    // images with fairly high associated histogram distances.  If we find that bugs are slipping
    // through we should revise this.
    assertTrue(ImageUtil.compareHistograms(first, second) < 2000.0);
  }

}
