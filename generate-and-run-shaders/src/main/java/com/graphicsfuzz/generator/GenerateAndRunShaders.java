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
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.lang3.tuple.Pair;

public class GenerateAndRunShaders {

  private static final int LIMIT = 10000;

  private static Namespace parse(String[] args) throws ArgumentParserException {
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

    parser.addArgument("output-dir")
          .help("Output directory.")
          .type(File.class);

    parser.addArgument("server")
          .help("URL of server.")
          .type(String.class);

    parser.addArgument("worker")
          .help("Worker name.")
          .type(String.class);

    parser.addArgument("glsl-version")
          .help("Version of GLSL to target.")
          .type(String.class);

    // Optional arguments
    Generate.addGeneratorCommonArguments(parser);

    parser.addArgument("--ignore-crash-strings")
        .help("File containing crash strings to ignore, one per line.")
        .type(File.class);

    return parser.parseArgs(args);

  }

  public static void main(String[] args)
      throws IOException, InterruptedException {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    }
  }

  public static void mainHelper(String[] args)
      throws IOException, InterruptedException, ArgumentParserException {
    final Namespace ns = parse(args);
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    final File referencesDir = ns.get("references");

    if (!fileOps.isDirectory(referencesDir)) {
      throw new IllegalArgumentException("References directory does not exist.");
    }
    final File donors = ns.get("donors");
    if (!fileOps.isDirectory(donors)) {
      throw new IllegalArgumentException("Donors directory does not exist.");
    }
    final File outputDir = ns.get("output_dir");
    fileOps.deleteDirectory(outputDir);
    fileOps.mkdir(outputDir);

    final ShadingLanguageVersion shadingLanguageVersion = ns.get("webgl")
        ? ShadingLanguageVersion.webGlFromVersionString(ns.get("glsl_version"))
        : ShadingLanguageVersion.fromVersionString(ns.get("glsl_version"));

    // Queue of shader jobs to be processed.
    final BlockingQueue<ShaderJob> queue =
        new LinkedBlockingQueue<>();

    final File crashStringsToIgnoreFile = ns.get("ignore_crash_strings");
    final Set<String> crashStringsToIgnore = new HashSet<>();
    if (crashStringsToIgnoreFile != null) {
      crashStringsToIgnore.addAll(fileOps.readLines(crashStringsToIgnoreFile));
    }
    File[] shaderJobFiles = fileOps.listShaderJobFiles(referencesDir, (dir, name) -> true);

    if (shaderJobFiles.length <= 0) {
      throw new IllegalArgumentException("No shader jobs found.");
    }

    final Thread consumer = new Thread(new ShaderConsumer(
        LIMIT,
        queue,
        outputDir,
        ns.get("server"),
        ns.get("worker"),
        crashStringsToIgnore,
        fileOps));
    consumer.start();

    final Thread producer = new Thread(new ShaderProducer(
        LIMIT,
        shaderJobFiles,
        queue,
        referencesDir,
        shadingLanguageVersion,
        donors,
        ns,
        fileOps));
    producer.start();

    consumer.join();
    producer.join();

  }

}
