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

package com.graphicsfuzz.common.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ExecHelper.RedirectType;
import com.graphicsfuzz.common.util.ExecResult;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.PruneUniforms;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.ToolHelper;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;

public class UniformPruner {

  private static Namespace parse(String[] args) {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("UniformPruner")
          .defaultHelp(true)
          .description("Prune uniforms from shader.");

    // Required arguments
    parser.addArgument("fragment_shader")
          .help("Path of fragment shader to be pruned.")
          .type(File.class);

    parser.addArgument("output_file")
          .help("Path of file into which pruned shader will be written.")
          .type(File.class);

    parser.addArgument("uniform_limit")
          .help("Number of uniforms to leave un-pruned.")
          .type(Integer.class);

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  public static void main(String[] args) throws Exception {
    final Namespace ns = parse(args);
    final File fragmentShader = ns.get("fragment_shader");
    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromShader(fragmentShader);
    final TranslationUnit tu = Helper.parse(fragmentShader, true);
    final File uniforms =
          new File(FilenameUtils.removeExtension(fragmentShader.getAbsolutePath()) + ".json");
    final UniformsInfo uniformsInfo = new UniformsInfo(uniforms);
    if (!(PruneUniforms.prune(tu, uniformsInfo, ns.get("uniform_limit"),
          Arrays.asList("GLF_dead", "GLF_live")))) {
      System.err.println("WARNING: it was not possible to prune sufficient uniforms.");
    }
    final File outputFile = new File(ns.getString("output_file"));
    Helper.emitShader(shadingLanguageVersion,
          ShaderKind.FRAGMENT,
          tu,
          Optional.empty(),
          outputFile);
    final File outputUniformsFile = new File(FilenameUtils.removeExtension(outputFile
        .getAbsolutePath()) + ".json");
    Helper.emitUniformsInfo(uniformsInfo, outputUniformsFile);

    final ExecResult execResult =
          ToolHelper.runValidatorOnShader(RedirectType.TO_BUFFER, outputFile);

    if (execResult.res != 0) {
      System.err.println("Pruned shader was not valid:");
      System.err.println(execResult.stdout);
      System.err.println(execResult.stderr);
      System.exit(1);
    }

  }

}
