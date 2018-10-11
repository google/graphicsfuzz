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
import com.graphicsfuzz.common.ast.visitors.AstBuilder;
import com.graphicsfuzz.parser.GLSLLexer;
import com.graphicsfuzz.parser.GLSLParser;
import com.graphicsfuzz.parser.GLSLParser.Translation_unitContext;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.apache.commons.io.FileUtils;

public class ParseHelper {

  static final String END_OF_HEADER = "// END OF GENERATED HEADER";

  public static Optional<TranslationUnit> maybeParseShader(File shader, boolean stripHeader)
      throws IOException, ParseTimeoutException {
    return shader.isFile()
        ? Optional.of(parse(shader, stripHeader))
        : Optional.empty();
  }

  public static synchronized TranslationUnit parse(File file, boolean stripHeader)
        throws IOException, ParseTimeoutException {
    return parseInputStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)),
          stripHeader);
  }

  public static synchronized TranslationUnit parse(String string, boolean stripHeader)
        throws IOException, ParseTimeoutException {
    return parseInputStream(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)),
          stripHeader);
  }

  private static synchronized TranslationUnit parseInputStream(InputStream input,
        boolean stripHeader)
        throws IOException, ParseTimeoutException {
    TranslationUnit result;
    if (stripHeader) {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      stripHeader(input, os);
      byte[] fileContents = os.toByteArray();
      return parseInputStream(new ByteArrayInputStream(fileContents));
    }
    return parseInputStream(input);
  }

  private static synchronized TranslationUnit parseInputStream(InputStream input)
        throws IOException, ParseTimeoutException {
    final int timeLimit = 60;

    ParseTreeListener listener =
          new TimeoutParseTreeListener(
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeLimit));
    Translation_unitContext ctx;
    try {
      try {
        ctx = tryFastParse(input, listener);
      } catch (ParseCancellationException exception) {
        input.reset();
        ctx = slowParse(input, listener);
      }
    } catch (ParseTimeoutRuntimeException exception) {
      throw new ParseTimeoutException(exception);
    }

    return AstBuilder.getTranslationUnit(ctx);
  }

  private static Translation_unitContext tryFastParse(
        InputStream inputStream,
        ParseTreeListener listener) throws IOException {

    GLSLParser parser = getParser(inputStream, listener);
    parser.setErrorHandler(new BailErrorStrategy());
    parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    Translation_unitContext result = parser.translation_unit();
    parser.getInterpreter().clearDFA();
    return result;
  }

  private static Translation_unitContext slowParse(
        InputStream inputStream,
        ParseTreeListener listener) throws IOException {

    GLSLParser parser = getParser(inputStream, listener);
    try {
      Translation_unitContext tu = parser.translation_unit();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new RuntimeException("Syntax errors occurred during parsing");
      }
      return tu;
    } finally {
      parser.getInterpreter().clearDFA();
    }
  }

  private static GLSLParser getParser(
        InputStream inputStream,
        ParseTreeListener listener) throws IOException {

    ANTLRInputStream input = new ANTLRInputStream(inputStream);
    GLSLLexer lexer = new GLSLLexer(input);
    PredictionContextCache cache = new PredictionContextCache();
    lexer.setInterpreter(
          new LexerATNSimulator(lexer, lexer.getATN(),
                lexer.getInterpreter().decisionToDFA, cache));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    GLSLParser parser = new GLSLParser(tokens);
    if (listener != null) {
      parser.addParseListener(listener);
    }
    parser.setInterpreter(
          new ParserATNSimulator(parser, parser.getATN(),
                parser.getInterpreter().decisionToDFA,
                cache));
    return parser;
  }

  public static void stripHeader(InputStream inputStream, OutputStream outputStream)
        throws IOException {

    // We do two kinds of header stripping:
    // (1) we strip the header from a variant, using END_OF_HEADER as a sentinel to know when to
    //     stop.
    // (2) we strip the header from a reference, which does not have END_OF_HEADER as a sentinel;
    //     we do this via baked in knowledge of how the start of the reference will look.
    //     Specifically, we chop once we have seen a balanced set of #ifdef and #endif macros.

    boolean isVariant = containsEndOfHeader(inputStream);
    inputStream.reset();
    try (
          BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));
          BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      boolean foundEndOfHeader = false;
      int ifdefEndIfDepth = 0;
      String line;
      while ((line = br.readLine()) != null) {
        if (!foundEndOfHeader) {
          if (isVariant) {
            if (line.trim().startsWith(END_OF_HEADER)) {
              foundEndOfHeader = true;
            }
          } else {
            if (line.trim().startsWith("#endif")) {
              assert ifdefEndIfDepth > 0;
              ifdefEndIfDepth--;
              if (ifdefEndIfDepth == 0) {
                foundEndOfHeader = true;
              }
            } else if (line.trim().startsWith("#ifdef")) {
              ifdefEndIfDepth++;
            }
          }
        } else {
          bw.write(line + "\n");
        }
      }
    }
  }

  private static boolean containsEndOfHeader(InputStream inputStream) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
    try {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.trim().startsWith(END_OF_HEADER)) {
          return true;
        }
      }
      return false;
    } finally {
      br.close();
    }
  }

}
