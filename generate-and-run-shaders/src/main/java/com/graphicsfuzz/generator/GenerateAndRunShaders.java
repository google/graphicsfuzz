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

package com.graphicsfuzz.generator;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class GenerateAndRunShaders {

  private static final int LIMIT = 10000;

  private static Namespace parse(String[] args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("GenerateAndRunShaders")
          .defaultHelp(true)
          .description("Generate and run a whole load of shaders.");

    // Required arguments
    parser.addArgument("references")
          .help("Path of folder of shaders to be transformed.")
          .type(File.class);

    parser.addArgument("donors")
          .help("Path of folder of donor shaders.")
          .type(File.class);

    parser.addArgument("output_dir")
          .help("Output directory.")
          .type(File.class);

    parser.addArgument("server")
          .help("URL of server.")
          .type(String.class);

    parser.addArgument("token")
          .help("Token for worker.")
          .type(String.class);

    parser.addArgument("glsl_version")
          .help("Version of GLSL to target.")
          .type(String.class);

    // Optional arguments
    Generate.addGeneratorCommonArguments(parser);

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException, ShaderDispatchException {
    final Namespace ns = parse(args);
    final File referencesDir = ns.get("references");
    if (!referencesDir.exists()) {
      throw new IllegalArgumentException("References directory does not exist.");
    }
    final File donors = ns.get("donors");
    if (!donors.exists()) {
      throw new IllegalArgumentException("Donors directory does not exist.");
    }
    final File outputDir = new File(ns.getString("output_dir"));
    FileUtils.deleteDirectory(outputDir);
    outputDir.mkdir();

    final ShadingLanguageVersion shadingLanguageVersion = ns.get("webgl")
        ? ShadingLanguageVersion.webGlFromVersionString(ns.get("glsl_version"))
        : ShadingLanguageVersion.fromVersionString(ns.get("glsl_version"));
    final boolean replaceFloatLiterals = ns.getBoolean("replace_float_literals");

    final List<File> referenceShaders = populateReferenceShaders(referencesDir);

    final BlockingQueue<ReferenceVariantPair> queue = new LinkedBlockingQueue<>();

    new Thread(new ShaderConsumer(
          LIMIT,
          queue,
          outputDir,
          ns.get("server"),
          ns.get("token"),
          referenceShaders,
        shadingLanguageVersion,
          replaceFloatLiterals
    )).start();

    new Thread(new ShaderProducer(
          LIMIT,
          queue,
          outputDir,
          referenceShaders,
        shadingLanguageVersion,
          replaceFloatLiterals,
          donors,
          ns
    )).start();

  }

  private static List<File> populateReferenceShaders(File shaders)
        throws IOException, ParseTimeoutException {
    final List<File> files = new ArrayList<>();
    for (File shader : shaders.listFiles((dir, name) -> name.endsWith(".frag"))) {
      final File uniforms = new File(FilenameUtils
            .removeExtension(shader.getAbsolutePath()) + ".json");
      if (!uniforms
            .exists()) {
        throw new IllegalArgumentException("Shader " + shader.getName()
              + " has no associated JSON file.");
      }
      final String license = FilenameUtils.removeExtension(shader.getAbsolutePath()) + ".license";
      if (!new File(license)
            .exists()) {
        throw new IllegalArgumentException("Shader " + shader.getName()
              + " has no associated license file.");
      }
      files.add(shader);
    }
    return files;
  }


}
