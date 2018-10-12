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
import com.graphicsfuzz.common.util.FileHelper;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class Util {

  static Map<String, Mat> referenceFileToImage = new HashMap<>();

  private Util() {
    // Utility class
  }

  static File[] getReferences() {
    return new File(TestShadersDirectory.getTestShadersDirectory(), "references")
        .listFiles((dir, name) -> name.endsWith(".frag"));
  }

  static Mat renderShaderIfNeeded(ShadingLanguageVersion shadingLanguageVersion, File originalShader,
      TemporaryFolder temporaryFolder, boolean stripHeader, ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException, ParseTimeoutException {
    if (!referenceFileToImage.containsKey(originalShader.getName())) {
      referenceFileToImage.put(
          originalShader.getName(),
          validateAndGetImage(
              ParseHelper.parse(originalShader, stripHeader),
              Optional.empty(),
              originalShader.getName() + ".reference.frag",
              shadingLanguageVersion,
              ShaderKind.FRAGMENT,
              temporaryFolder,
              fileOps)
          );
    }
    return referenceFileToImage.get(originalShader.getName());
  }

  static Mat validateAndGetImage(
      File shaderFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {

    validate(shaderFile);
    return getImage(shaderFile, temporaryFolder, fileOps);
  }

  static Mat getImage(
      File shaderFile,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps) throws IOException, InterruptedException {

    final Optional<String> shaderTranslatorArg = getShaderTranslatorArg(shaderFile, fileOps);
    if (shaderTranslatorArg.isPresent()) {
      final ExecResult shaderTranslatorResult = ToolHelper
            .runShaderTranslatorOnShader(RedirectType.TO_BUFFER, shaderFile,
                  shaderTranslatorArg.get());
      assertEquals(0, shaderTranslatorResult.res);
    }
    return getImageUsingSwiftshader(shaderFile, temporaryFolder);
  }

  static void validate(File shaderFile) throws IOException, InterruptedException {
    final ExecResult validatorResult = ToolHelper
          .runValidatorOnShader(RedirectType.TO_BUFFER, shaderFile);
    if (validatorResult.res != 0) {
      assert false;
    }
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

  static Mat validateAndGetImage(
      TranslationUnit tu,
      Optional<UniformsInfo> uniforms,
      String fileName,
      ShadingLanguageVersion shadingLanguageVersion,
      ShaderKind shaderKind,
      TemporaryFolder temporaryFolder,
      ShaderJobFileOperations fileOps)
      throws IOException, InterruptedException {

    // Using fileOps here, even though the rest of the code does not yet use it.
    Assert.assertEquals(shaderKind, ShaderKind.FRAGMENT);
    Assert.assertTrue(fileName.endsWith(".frag"));
    Assert.assertTrue(uniforms.isPresent());

    final ShaderJob shaderJob = new GlslShaderJob(
        Optional.empty(),
        Optional.of(tu),
        uniforms.get(),
        Optional.empty()
    );

    final File shaderJobFileOutput = new File(
        temporaryFolder.getRoot(),
        FilenameUtils.removeExtension(fileName) + ".json");

    fileOps.writeShaderJobFile(shaderJob, shadingLanguageVersion, shaderJobFileOutput);

    // TODO: Use fileOps more.

    final File tempFile = FileHelper.replaceExtension(shaderJobFileOutput, ".frag");

    return validateAndGetImage(tempFile, temporaryFolder, fileOps);
  }

  static Mat getImageUsingSwiftshader(File shaderFile, TemporaryFolder temporaryFolder) throws IOException, InterruptedException {
    File imageFile = temporaryFolder.newFile();
    ExecResult res =
        ToolHelper.runSwiftshaderOnShader(RedirectType.TO_BUFFER,
            shaderFile,
            imageFile,
            false,
            32,
            32);

    assertEquals(0, res.res);
    Mat mat = opencv_imgcodecs.imread(imageFile.getAbsolutePath());
    assertNotNull(mat);
    return mat;
  }

  static File createDonorsFolder(TemporaryFolder temporaryFolder) throws IOException {
    final File[] originalShaderFiles = Util.getReferences();
    final File donorsFolder = temporaryFolder.newFolder();
    for (File originalShader : originalShaderFiles) {
      FileUtils.copyFile(originalShader,
          Paths.get(donorsFolder.getAbsolutePath(), originalShader.getName()).toFile());
    }
    return donorsFolder;
  }

  static void assertImagesEquals(Mat first, Mat second) {
    UByteIndexer firstI = first.createIndexer();
    UByteIndexer secondI = second.createIndexer();
    assertEquals(firstI.rows(), secondI.rows());
    assertEquals(firstI.cols(), secondI.cols());
    for (int y = 0; y < firstI.rows(); y++) {
      for (int x = 0; x < firstI.cols(); x++) {
        assertEquals(firstI.get(y, x), secondI.get(y, x));
      }
    }

  }
}
