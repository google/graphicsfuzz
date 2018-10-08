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
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.generator.tool.Generate;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

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

    parser.addArgument("--ignore_crash_strings")
        .help("File containing crash strings to ignore, one per line.")
        .type(File.class);

    parser.addArgument("--only_variants")
        .help("Only run variant shaders (so sacrifice finding wrong images in favour of crashes.")
        .type(Boolean.class)
        .action(Arguments.storeTrue());

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }

  public static void main(String[] args)
      throws IOException, InterruptedException {
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

    final BlockingQueue<Pair<ShaderJob, ShaderJob>> queue =
        new LinkedBlockingQueue<>();

    final File crashStringsToIgnoreFile = ns.get("ignore_crash_strings");
    final Set<String> crashStringsToIgnore = new HashSet<>();
    if (crashStringsToIgnoreFile != null) {
      crashStringsToIgnore.addAll(FileUtils.readLines(crashStringsToIgnoreFile,
          StandardCharsets.UTF_8));
    }

    final List<String> shaderJobPrefixes =
        Arrays.stream(referencesDir.listFiles((dir, name) -> name.endsWith(".json")))
            .map(item -> FilenameUtils.removeExtension(item.getName()))
            .collect(Collectors.toList());
    if (shaderJobPrefixes.isEmpty()) {
      throw new IllegalArgumentException("No shader jobs found.");
    }

    final Thread consumer = new Thread(new ShaderConsumer(
          LIMIT,
          queue,
          outputDir,
          ns.get("server"),
          ns.get("token"),
          shadingLanguageVersion,
          crashStringsToIgnore,
          ns
    ));
    consumer.start();

    final Thread producer = new Thread(new ShaderProducer(
          LIMIT,
          shaderJobPrefixes,
          new RandomWrapper(ns.get("seed")),
          queue,
          referencesDir,
          shadingLanguageVersion,
          donors,
          ns
    ));
    producer.start();

    consumer.join();
    producer.join();

  }

}
