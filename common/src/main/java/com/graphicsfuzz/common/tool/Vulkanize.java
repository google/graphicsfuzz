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
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.util.AvoidDeprecatedGlFragColor;
import com.graphicsfuzz.common.util.ConvertToVulkanFormat;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.common.util.UniformsInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;

public class Vulkanize {

  private static Namespace parse(String[] args) {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("Vulkanize")
        .defaultHelp(true)
        .description("Convert to Vulkan format.");

    // Required arguments
    parser.addArgument("fragment_shader")
        .help("Path of fragment shader to be converted.")
        .type(File.class);

    parser.addArgument("output_prefix")
        .help("Filename, without extension, of target shader.")
        .type(File.class);

    // Optional arguments
    parser.addArgument("--strip_header")
        .help("Strip header from start of shader (header assumed to be present if this is set, "
            + "and assumed not to be otherwise.")
        .action(Arguments.storeTrue());

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  public static void main(String[] args) throws Exception {

    Namespace ns = parse(args);

    final File fragmentShader = ns.get("fragment_shader");

    TranslationUnit tu = Helper.parse(fragmentShader, ns.getBoolean("strip_header"));

    UniformsInfo uniformsInfo = new UniformsInfo(
        new File(FilenameUtils
              .removeExtension(fragmentShader.getAbsolutePath()) + ".json"));

    ConvertToVulkanFormat.convert(tu, uniformsInfo);

    AvoidDeprecatedGlFragColor.avoidDeprecatedGlFragColor(tu, Constants.GLF_COLOR);

    Helper.emitShader(ShadingLanguageVersion.GLSL_450, ShaderKind.FRAGMENT, tu,
        new PrintStream(new FileOutputStream(ns.getString("output_prefix") + ".frag")));

    Helper.emitUniformsInfo(uniformsInfo, new PrintStream(
        new FileOutputStream(ns.getString("output_prefix") + ".json")));

  }

}
