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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.FloatLiteralReplacer;
import java.io.File;
import java.io.IOException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class PrepareReference {

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("PrepareReference")
        .defaultHelp(true)
        .description("Prepare a reference shader job to work with GraphicsFuzz.");

    // Required arguments
    parser.addArgument("reference")
        .help("Reference shader job file (.json) to be prepared.")
        .type(File.class);

    parser.addArgument("output")
        .help("Output shader job file (.json).")
        .type(File.class);

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

    parser.addArgument("--generate_uniform_bindings")
        .help("Put all uniforms in uniform blocks and generate bindings; required for Vulkan "
            + "compatibility.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max_uniforms")
        .help("Check that reference doesn't have too many uniforms; required for Vulkan "
            + "compatibility.")
        .setDefault(0)
        .type(Integer.class);

    return parser.parseArgs(args);
  }


  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (IOException | ParseTimeoutException | InterruptedException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

  public static void mainHelper(String[] args) throws ArgumentParserException, IOException,
      ParseTimeoutException, InterruptedException {

    Namespace ns = parse(args);

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final ShadingLanguageVersion shadingLanguageVersion =
        ns.getBoolean("webgl")
            ? ShadingLanguageVersion.webGlFromVersionString(ns.get("glsl_version"))
            : ShadingLanguageVersion.fromVersionString(ns.get("glsl_version"));

    prepareReference(
        ns.get("reference"),
        ns.get("output"),
        shadingLanguageVersion,
        ns.get("replace_float_literals"),
        ns.get("max_uniforms"),
        ns.get("generate_uniform_bindings"),
        fileOps);


  }

  public static void prepareReference(
      File referenceShaderJobFile,
      File outputShaderJobFile,
      ShadingLanguageVersion shadingLanguageVersion,
      boolean replaceFloatLiterals,
      int maxUniforms,
      boolean generateUniformBindings,
      ShaderJobFileOperations fileOps) throws IOException, ParseTimeoutException,
      InterruptedException {

    final ShaderJob shaderJob = fileOps.readShaderJobFile(referenceShaderJobFile);

    prepareReference(
        shaderJob,
        shadingLanguageVersion,
        replaceFloatLiterals,
        maxUniforms,
        generateUniformBindings);

    fileOps.writeShaderJobFile(shaderJob, outputShaderJobFile);
  }

  public static void prepareReference(ShaderJob shaderJob,
                                      ShadingLanguageVersion shadingLanguageVersion,
                                      boolean replaceFloatLiterals,
                                      int maxUniforms,
                                      boolean generateUniformBindings) {

    for (TranslationUnit tu : shaderJob.getShaders()) {
      prepareReferenceShader(
          tu,
          shadingLanguageVersion,
          replaceFloatLiterals,
          shaderJob.getUniformsInfo());
    }

    if (maxUniforms > 0 && shaderJob.getUniformsInfo().getNumUniforms() > maxUniforms) {
      throw new RuntimeException("Too many uniforms in reference shader job.");
    }

    if (generateUniformBindings) {
      shaderJob.makeUniformBindings();
    }
  }

  private static void prepareReferenceShader(TranslationUnit tu,
                                             ShadingLanguageVersion shadingLanguageVersion,
                                             boolean replaceFloatLiterals,
                                             UniformsInfo uniformsInfo) {
    uniformsInfo.zeroUnsetUniforms(tu);
    if (replaceFloatLiterals) {
      FloatLiteralReplacer.replace(tu, uniformsInfo, shadingLanguageVersion);
    }
    // TODO: at the moment we assume during generation that shaders do not have version strings.
    // It may turn out to be good to change this, but for now we assert it to document the
    // decision.
    assert !tu.hasShadingLanguageVersion();
    tu.setShadingLanguageVersion(shadingLanguageVersion);
  }

}
