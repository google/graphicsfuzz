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
import static org.junit.Assert.assertNotNull;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ToolHelper;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.junit.rules.TemporaryFolder;

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
      TemporaryFolder temporaryFolder, boolean stripHeader)
      throws IOException, InterruptedException, ParseTimeoutException {
    if (!referenceFileToImage.containsKey(originalShader.getName())) {
      referenceFileToImage.put(originalShader.getName(),
          validateAndGetImage(ParseHelper.parse(originalShader, stripHeader),
                Optional.empty(),
                originalShader.getName() + ".reference.frag", shadingLanguageVersion,
                ShaderKind.FRAGMENT, temporaryFolder)
          );
    }
    return referenceFileToImage.get(originalShader.getName());
  }

  static Mat validateAndGetImage(File shaderFile,
        TemporaryFolder temporaryFolder)
        throws IOException, InterruptedException {
    validate(shaderFile);
    return getImage(shaderFile, temporaryFolder);
  }

  static Mat getImage(File shaderFile, TemporaryFolder temporaryFolder)
        throws IOException, InterruptedException {
    final Optional<String> shaderTranslatorArg = getShaderTranslatorArg(shaderFile);
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

  private static Optional<String> getShaderTranslatorArg(File shaderFile) throws IOException {
    Optional<String> shaderTranslatorArgs;
    final ShadingLanguageVersion shadingLanguageVersionFromShader = ShadingLanguageVersion.getGlslVersionFromShader(shaderFile);
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

  static Mat validateAndGetImage(TranslationUnit tu, Optional<UniformsInfo> uniforms, String fileName, ShadingLanguageVersion shadingLanguageVersion,
      ShaderKind shaderKind,
      TemporaryFolder temporaryFolder)
      throws IOException, InterruptedException {
    File tempFile = writeShaderAndUniformsToFile(tu, uniforms, fileName, shadingLanguageVersion, shaderKind,
          temporaryFolder);
    return validateAndGetImage(tempFile, temporaryFolder);
  }

  static File writeShaderAndUniformsToFile(TranslationUnit tu,
        Optional<UniformsInfo> uniforms, String fileName, ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TemporaryFolder temporaryFolder) throws IOException {
    File tempFile = new File(temporaryFolder.getRoot(), fileName);
    if (tempFile.exists()) {
      tempFile.delete();
    }
    Helper.emitShader(shadingLanguageVersion, shaderKind, tu,
        new PrintStream(new FileOutputStream(tempFile)));
    if (uniforms.isPresent()) {
      final File uniformsFile = new File(
            FilenameUtils.removeExtension(tempFile.getAbsolutePath()) + ".json");
      if (uniformsFile.exists()) {
        uniformsFile.delete();
      }
      Helper.emitUniformsInfo(uniforms.get(), new PrintStream(new FileOutputStream(
            uniformsFile
      )));
    }
    return tempFile;
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
