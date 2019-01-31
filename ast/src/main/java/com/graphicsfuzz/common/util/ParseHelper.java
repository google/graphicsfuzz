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
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.parser.GLSLLexer;
import com.graphicsfuzz.parser.GLSLParser;
import com.graphicsfuzz.parser.GLSLParser.Translation_unitContext;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolPaths;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.apache.commons.io.FilenameUtils;

public class ParseHelper {

  public static final String END_OF_GRAPHICSFUZZ_DEFINES = "// END OF GENERATED HEADER";

  public static Optional<TranslationUnit> maybeParseShader(File shader)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return shader.isFile()
        ? Optional.of(parse(shader))
        : Optional.empty();
  }

  public static synchronized TranslationUnit parse(File file)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return parseInputStream(new ByteArrayInputStream(FileUtils.readFileToByteArray(file)),
        ShaderKind.fromExtension(FilenameUtils.getExtension(file.getName())));
  }

  public static synchronized TranslationUnit parse(String string, ShaderKind shaderKind)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return parseInputStream(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)),
        shaderKind);
  }

  /**
   * Parses a shader from a given string.  The shader is assumed to be a fragment shader;
   * typically the shader kind is unimportant when we parse from strings.
   * @param string The shader text to be parsed.
   * @return The parsed shader.
   * @throws IOException Thrown if parsing leads to an IO exception.
   * @throws ParseTimeoutException Thrown if parsing takes to long.
   */
  public static synchronized TranslationUnit parse(String string)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    return parse(string, ShaderKind.FRAGMENT);
  }

  private static synchronized TranslationUnit parseInputStream(InputStream input,
                                                               ShaderKind shaderKind)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    final boolean hasWebGlHint = checkForWebGlHint(input);

    // Strip special GraphicsFuzz defines and run preprocessor
    final InputStream preprocessedInput = preprocess(stripGraphicsFuzzDefines(input), shaderKind);

    // Parse the preprocessed shader
    final int timeLimit = 60;
    ParseTreeListener listener =
        new TimeoutParseTreeListener(
            System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeLimit));
    Translation_unitContext ctx;
    try {
      try {
        ctx = tryFastParse(preprocessedInput, listener);
      } catch (ParseCancellationException exception) {
        preprocessedInput.reset();
        ctx = slowParse(preprocessedInput, listener);
      }
    } catch (ParseTimeoutRuntimeException exception) {
      throw new ParseTimeoutException(exception);
    }

    return AstBuilder.getTranslationUnit(ctx, shaderKind, hasWebGlHint);
  }

  private static boolean checkForWebGlHint(InputStream input) throws IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
      if (br.readLine() == null) {
        return false;
      }
      final String maybeHint = br.readLine();
      if (maybeHint == null) {
        return false;
      }
      return ShadingLanguageVersion.isWebGlHint(maybeHint);
    } finally {
      input.reset();
    }
  }

  private static InputStream preprocess(InputStream inputStream, ShaderKind shaderKind)
      throws IOException,
      InterruptedException {
    // Preprocess the shader using glslangValidator
    final ExecResult preprocessorResult = new ExecHelper().exec(ExecHelper.RedirectType.TO_BUFFER,
        null,
        false,
        inputStream,
        ToolPaths.glslangValidator(), "-E", "--stdin", "-S", shaderKind.getFileExtension());
    if (preprocessorResult.res != 0) {
      throw new RuntimeException("Preprocessing failed with exit code " + preprocessorResult.res
          + ": " + preprocessorResult.stderr);
    }
    return new ByteArrayInputStream(preprocessorResult.stdout
        .toString().getBytes(StandardCharsets.UTF_8));
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
        ParseTreeListener listener) throws IOException, GlslParserException {

    GLSLParser parser = getParser(inputStream, listener);
    try {
      Translation_unitContext tu = parser.translation_unit();
      if (parser.getNumberOfSyntaxErrors() > 0) {
        throw new GlslParserException(parser);
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

  static InputStream stripGraphicsFuzzDefines(InputStream inputStream)
        throws IOException {
    if (!containsEndOfGraphicsFuzzDefines(inputStream)) {
      return inputStream;
    }
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      boolean passedEndOfGraphicsFuzzDefines = false;
      String line;
      while ((line = br.readLine()) != null) {
        if (passedEndOfGraphicsFuzzDefines
            || isVersion(line)
            || ShadingLanguageVersion.isWebGlHint(line)) {
          bw.write(line + "\n");
        } else {
          if (line.trim().startsWith(END_OF_GRAPHICSFUZZ_DEFINES)) {
            passedEndOfGraphicsFuzzDefines = true;
          }
        }
      }
    }
    byte[] fileContents = outputStream.toByteArray();
    return new ByteArrayInputStream(fileContents);
  }

  private static boolean isVersion(String line) {
    Pattern pattern = Pattern.compile("\\s*#\\s*version\\s*\\d+\\s*\\w*\\s*");
    Matcher matcher = pattern.matcher(line);
    return matcher.find();
  }

  private static boolean containsEndOfGraphicsFuzzDefines(InputStream inputStream)
      throws IOException {
    boolean result = false;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.trim().startsWith(END_OF_GRAPHICSFUZZ_DEFINES)) {
          result = true;
          break;
        }
      }
    }
    // Reset the input stream so that we can consume it again once we know the answer to the
    // question of whether the GraphicsFuzz defines are present.
    inputStream.reset();
    return result;
  }

}
