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
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.FloatLiteralReplacer;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class PrepareReference {

  private static Namespace parse(String[] args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("PrepareReference")
        .defaultHelp(true)
        .description("Prepare a reference shader job to work with GraphicsFuzz.");

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

    parser.addArgument("--generate_uniform_bindings")
        .help("Put all uniforms in uniform blocks and generate bindings; required for Vulkan "
            + "compatibility.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max_uniforms")
        .help("Check that reference doesn't have too many uniforms; required for Vulkan "
            + "compatibility.")
        .setDefault(0)
        .type(Integer.class);

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

      final ShadingLanguageVersion shadingLanguageVersion =
          ns.getBoolean("webgl")
              ? ShadingLanguageVersion.webGlFromVersionString(ns.getString("glsl_version"))
              : ShadingLanguageVersion.fromVersionString(ns.getString("glsl_version"));

      prepareReference(ns.getString("reference_prefix"),
          new File("."),
          ns.getString("output_prefix"),
          shadingLanguageVersion,
          ns.getBoolean("replace_float_literals"),
          ns.get("max_uniforms"),
          ns.getBoolean("generate_uniform_bindings"));

    } catch (Throwable throwable) {
      throwable.printStackTrace();
      System.exit(1);
    }

  }

  public static void prepareReference(String referencePrefix,
                                       File workDir,
                                       String outputPrefix,
                                       ShadingLanguageVersion shadingLanguageVersion,
                                       boolean replaceFloatLiterals,
                                       int maxUniforms,
                                       boolean generateUniformBindings)
      throws IOException, ParseTimeoutException {
    final File jsonFile = new File(referencePrefix + ".json");
    final File vertexShaderFile =
        new File(referencePrefix + ShaderKind.VERTEX.getFileExtension());
    final File fragmentShaderFile =
        new File(referencePrefix + ShaderKind.FRAGMENT.getFileExtension());

    final GlslShaderJob shaderJob = new GlslShaderJob(
        vertexShaderFile.isFile()
            ? Optional.of(Helper.parse(vertexShaderFile, false))
            : Optional.empty(),
        fragmentShaderFile.isFile()
            ? Optional.of(Helper.parse(fragmentShaderFile, false))
            : Optional.empty(),
        new UniformsInfo(jsonFile));

    if (shaderJob.hasVertexShader()) {
      prepareReferenceShader(shaderJob.getVertexShader(),
          shadingLanguageVersion,
          replaceFloatLiterals,
          shaderJob.getUniformsInfo());
    }

    if (shaderJob.hasFragmentShader()) {
      prepareReferenceShader(shaderJob.getFragmentShader(),
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

    Helper.emitShaderJob(shaderJob, shadingLanguageVersion, outputPrefix, workDir,
        new File(referencePrefix + ".license"));
  }

  private static void prepareReferenceShader(TranslationUnit tu,
                                             ShadingLanguageVersion shadingLanguageVersion,
                                             boolean replaceFloatLiterals,
                                             UniformsInfo uniformsInfo) {
    uniformsInfo.zeroUnsetUniforms(tu);
    if (replaceFloatLiterals) {
      FloatLiteralReplacer.replace(tu, uniformsInfo, shadingLanguageVersion);
    }
  }

}
