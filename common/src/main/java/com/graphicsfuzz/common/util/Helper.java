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

package com.graphicsfuzz.common.util;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.io.FilenameUtils;

public final class Helper {

  private Helper() {
    // Utility class
  }

  public static void emitDefines(PrintStream out, ShadingLanguageVersion version,
        ShaderKind shaderKind,
        boolean defineMacros) {
    EmitShaderHelper.emitDefines(out, version, shaderKind,
          defineMacros ? Helper::glfMacros : () -> new StringBuilder(),
          Optional.empty());
  }

  public static StringBuilder glfMacros() {
    StringBuilder sb = new StringBuilder();
    sb.append("#ifndef REDUCER\n");
    sb.append("#define " + Constants.GLF_ZERO + "(X, Y)          (Y)\n");
    sb.append("#define " + Constants.GLF_ONE + "(X, Y)           (Y)\n");
    sb.append("#define " + Constants.GLF_FALSE + "(X, Y)         (Y)\n");
    sb.append("#define " + Constants.GLF_TRUE + "(X, Y)          (Y)\n");
    sb.append("#define " + Constants.GLF_IDENTITY + "(X, Y)      (Y)\n");
    sb.append("#define " + Constants.GLF_DEAD + "(X)             (X)\n");
    sb.append("#define " + Constants.GLF_FUZED + "(X)           (X)\n");
    sb.append("#define " + Constants.GLF_WRAPPED_LOOP + "(X)     X\n");
    sb.append("#define " + Constants.GLF_WRAPPED_IF_TRUE + "(X)  X\n");
    sb.append("#define " + Constants.GLF_WRAPPED_IF_FALSE + "(X) X\n");
    sb.append("#define " + Constants.GLF_SWITCH + "(X)           X\n");
    sb.append("#endif\n");
    sb.append("\n");
    sb.append(ParseHelper.END_OF_HEADER + "\n");
    sb.append("\n");
    return sb;
  }

  public static void emitShader(ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        PrintStream stream) {
    EmitShaderHelper.emitShader(shadingLanguageVersion, shaderKind, shader, license,
          stream,
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          Helper::glfMacros);
  }

  public static void emitShader(
        ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        PrintStream stream) {
    emitShader(shadingLanguageVersion, shaderKind, shader, Optional.empty(), stream);
  }

  public static void emitShader(
        ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        File outputFile) throws FileNotFoundException {
    emitShader(shadingLanguageVersion, shaderKind, shader, license, outputFile,
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          true);
  }

  public static void emitShader(ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        File outputFile,
        int indentationWidth,
        Supplier<String> newlineSupplier,
        boolean defineMacros) throws FileNotFoundException {
    EmitShaderHelper.emitShader(shadingLanguageVersion, shaderKind, shader, license,
          outputFile,
          indentationWidth,
          newlineSupplier,
          defineMacros ? Helper::glfMacros : () -> new StringBuilder());
  }

  public static String jsonFilenameForShader(String shader) {
    return FilenameUtils.removeExtension(shader) + ".json";
  }

  public static TranslationUnit parse(String string, boolean stripHeader)
      throws IOException, ParseTimeoutException {
    return ParseHelper.parse(string, stripHeader);
  }

  public static TranslationUnit parse(File shader, boolean stripHeader)
      throws IOException,ParseTimeoutException {
    return ParseHelper.parse(shader, stripHeader);
  }

  public static Optional<String> readLicenseFile(File licenseFile) throws IOException {
    if (licenseFile == null || !licenseFile.isFile()) {
      return Optional.empty();
    }
    StringBuilder result = new StringBuilder();
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(licenseFile));
    } catch (FileNotFoundException exception) {
      System.err.println("License file " + licenseFile + " not found.");
      throw exception;
    }
    String line;
    while ((line = br.readLine()) != null) {
      result.append(line + "\n");
    }
    return Optional.of(result.toString());
  }

}
