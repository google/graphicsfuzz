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

package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.FloatLiteralReplacer;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class PrepareReference {

  private static Namespace parse(String[] args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("PrepareReference")
        .defaultHelp(true)
        .description("Prepare a reference to work with GLFuzz.");

    // Required arguments
    parser.addArgument("reference_prefix")
        .help("Prefix of reference shader(s) to be prepared.")
        .type(String.class);

    parser.addArgument("output_prefix")
        .help("Prefix of target file name, e.g. \"foo\" if generated fragment shader is to be "
            + "\"foo.frag\".")
        .type(String.class);

    parser.addArgument("glsl_version")
        .help("Version of GLSL to target.")
        .type(String.class);

    // Optional arguments
    parser.addArgument("--replace_float_literals")
        .help("Replace float literals with uniforms.")
        .action(Arguments.storeTrue());

    parser.addArgument("--webgl")
        .help("Use WebGL spec.")
        .action(Arguments.storeTrue());

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  public static void main(String[] args) throws IOException, InterruptedException {

    Namespace ns = parse(args);

    try {

      final String referencePrefix = ns.getString("reference_prefix");
      final boolean replaceFloatLiterals = ns.getBoolean("replace_float_literals");
      final ShadingLanguageVersion shadingLanguageVersion =
          ns.getBoolean("webgl")
              ? ShadingLanguageVersion.webGlFromVersionString(ns.getString("glsl_version"))
              : ShadingLanguageVersion.fromVersionString(ns.getString("glsl_version"));
      final File workDir = new File(".");
      final String outputPrefix = ns.getString("output_prefix");

      prepareReference(referencePrefix, workDir, outputPrefix, shadingLanguageVersion,
          replaceFloatLiterals);

    } catch (Throwable throwable) {
      throwable.printStackTrace();
      System.exit(1);
    }

  }

  public static void prepareReference(String referencePrefix, File workDir,
      String outputPrefix, ShadingLanguageVersion shadingLanguageVersion,
      boolean replaceFloatLiterals)
        throws IOException, ParseTimeoutException {

    final File uniforms = new File(referencePrefix + ".json");
    final UniformsInfo uniformsInfo = new UniformsInfo(uniforms);

    if (new File(referencePrefix + ShaderKind.FRAGMENT.getFileExtension()).isFile()) {
      prepareReferenceShader(referencePrefix, ShaderKind.FRAGMENT, workDir, outputPrefix,
          shadingLanguageVersion,
          replaceFloatLiterals,
          uniformsInfo);
    }

    if (new File(referencePrefix + ShaderKind.VERTEX.getFileExtension()).isFile()) {
      prepareReferenceShader(referencePrefix, ShaderKind.VERTEX, workDir, outputPrefix,
          shadingLanguageVersion,
          replaceFloatLiterals,
          uniformsInfo);
    }

    emitUniforms(workDir, outputPrefix, uniformsInfo);

  }

  private static void prepareReferenceShader(String referencePrefix, ShaderKind shaderKind,
      File workDir, String outputPrefix, ShadingLanguageVersion shadingLanguageVersion,
      boolean replaceFloatLiterals,
      UniformsInfo uniformsInfo)
      throws IOException, ParseTimeoutException {
    final File referenceShader = new File(referencePrefix + shaderKind.getFileExtension());
    TranslationUnit tu = ParseHelper.parse(referenceShader, false);
    uniformsInfo.zeroUnsetUniforms(tu);
    if (replaceFloatLiterals) {
      // TODO: need to adjust this to take account of multiple shader kinds.
      FloatLiteralReplacer.replace(tu, uniformsInfo, shadingLanguageVersion);
    }
    final File outputFile = new File(workDir, outputPrefix + shaderKind.getFileExtension());
    Helper.emitShader(shadingLanguageVersion, shaderKind, tu,
        Helper.readLicenseFile(new File(referencePrefix + ".license")),
        outputFile);
  }

  private static void emitUniforms(File workDir, String outputPrefix,
        UniformsInfo uniformsInfo)
      throws FileNotFoundException {
    PrintStream stream = new PrintStream(new FileOutputStream(
        new File(workDir, outputPrefix + ".json")));
    stream.println(uniformsInfo);
    stream.close();
  }

}
