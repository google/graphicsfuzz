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

  public static void emitShader(TranslationUnit shader,
                                Optional<String> license,
                                PrintStream stream,
                                int indentationWidth,
                                Supplier<String> newlineSupplier) {
    PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(stream, indentationWidth, newlineSupplier,
        true, license);
    ppv.visit(shader);
  }

  public static void emitShader(TranslationUnit shader,
                                Optional<String> license,
                                File outputFile,
                                int indentationWidth,
                                Supplier<String> newlineSupplier) throws FileNotFoundException {
    try (PrintStream stream = new PrintStream(new FileOutputStream(outputFile))) {
      emitShader(
          shader,
          license,
          stream,
          indentationWidth,
          newlineSupplier
      );
    }
  }

  public static void emitShader(
      TranslationUnit shader,
      Optional<String> license,
      File outputFile) throws FileNotFoundException {
    EmitShaderHelper.emitShader(shader, license, outputFile,
        PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
        PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER
    );
  }

}
