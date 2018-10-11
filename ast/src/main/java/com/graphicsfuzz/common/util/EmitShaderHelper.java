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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Supplier;

public final class EmitShaderHelper {

  private EmitShaderHelper() {
    // Utility class
  }

  public static void emitDefines(PrintStream out, ShadingLanguageVersion version,
        ShaderKind shaderKind,
        Supplier<StringBuilder> extraMacros,
        Optional<String> license) {
    out.print(getDefinesString(version,
          shaderKind,
          extraMacros, license).toString());
  }

  public static StringBuilder getDefinesString(ShadingLanguageVersion version,
        ShaderKind shaderKind,
        Supplier<StringBuilder> extraMacros,
        Optional<String> license) {
    final StringBuilder sb = new StringBuilder();
    sb.append("#version " + version.getVersionString() + "\n");
    if (license.isPresent()) {
      sb.append("//\n");
      sb.append("// Adapted from an original shader with copyright and license as follows:\n");
      sb.append("//\n");
      sb.append(license.get() + "\n");
    }
    if (version.isWebGl()) {
      sb.append("//WebGL\n");
    }
    sb.append("\n");
    sb.append("#ifdef GL_ES\n");
    sb.append("#ifdef GL_FRAGMENT_PRECISION_HIGH\n");
    sb.append("precision highp float;\n");
    sb.append("precision highp int;\n");
    sb.append("#else\n");
    sb.append("precision mediump float;\n");
    sb.append("precision mediump int;\n");
    sb.append("#endif\n");
    sb.append("#endif\n");
    sb.append("\n");
    sb.append(extraMacros.get());
    return sb;
  }

  public static void emitShader(ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        PrintStream stream,
        int indentationWidth,
        Supplier<String> newlineSupplier,
        Supplier<StringBuilder> extraMacros) {
    emitDefines(stream, shadingLanguageVersion, shaderKind, extraMacros, license);
    PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(stream, indentationWidth, newlineSupplier);
    ppv.visit(shader);
  }

  public static void emitShader(ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        File outputFile,
        int indentationWidth,
        Supplier<String> newlineSupplier,
        Supplier<StringBuilder> extraMacros) throws FileNotFoundException {
    try (PrintStream stream = new PrintStream(new FileOutputStream(outputFile))) {
      emitShader(
          shadingLanguageVersion,
          shaderKind,
          shader,
          license,
          stream,
          indentationWidth,
          newlineSupplier,
          extraMacros);
    }
  }

  public static void emitShader(ShadingLanguageVersion shadingLanguageVersion,
        ShaderKind shaderKind,
        TranslationUnit shader,
        Optional<String> license,
        File outputFile) throws FileNotFoundException {
    try (PrintStream stream = new PrintStream(new FileOutputStream(outputFile))) {
      emitShader(shadingLanguageVersion, shaderKind, shader, license,
          stream,
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          () -> new StringBuilder());
    }
  }

}
