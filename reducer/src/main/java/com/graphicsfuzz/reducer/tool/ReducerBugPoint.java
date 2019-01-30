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

package com.graphicsfuzz.reducer.tool;

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;

public class ReducerBugPoint {

  private static final int STEP_LIMIT = 50;

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("ReducerBugPoint")
          .defaultHelp(true)
          .description("Find bugs in the reducer.");

    // Required arguments
    parser.addArgument("shader")
          .help("Path of shader job file (.json) to be analysed.")
          .type(File.class);

    parser.addArgument("--output")
          .help("Output directory.")
          .setDefault(new File("."))
          .type(File.class);

    // Optional arguments
    parser.addArgument("--max-iterations")
          .help("Maximum number of times to iterate before giving up.")
          .setDefault(30)
          .type(Integer.class);

    parser.addArgument("--seed")
          .help("Seed to initialize random number generator with.")
          .type(Integer.class);

    parser.addArgument("--preserve-semantics")
          .help("Only perform semantics-preserving reductions.")
          .action(Arguments.storeTrue());

    parser.addArgument("--verbose")
          .help("Emit verbose info.")
          .action(Arguments.storeTrue());

    parser.addArgument("--exception-on-invalid")
          .help("Throw exception when shader is invalid.")
          .action(Arguments.storeTrue());

    parser.addArgument("--expected-string")
          .help("A string to look for in the exception message.")
          .type(String.class);

    return parser.parseArgs(args);
  }


  public static void main(String[] args)
      throws IOException, ParseTimeoutException, ArgumentParserException, InterruptedException,
      GlslParserException {

    final Namespace ns = parse(args);

    final int seed = ArgsUtil.getSeedArgument(ns);

    final int maxIterations = ns.get("max_iterations");

    final boolean reduceEverywhere = !ns.getBoolean("preserve_semantics");

    final boolean verbose = ns.get("verbose");

    final String expectedString = ns.getString("expected_string") == null
          ? ""
          : ns.getString("expected_string");

    final IRandom generator = new RandomWrapper(seed);

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    final File interestingShaderJobFile = new File("interesting.frag");

    if (fileOps.doesShaderJobExist(interestingShaderJobFile)) {
      fileOps.deleteShaderJobFile(interestingShaderJobFile);
    }

    final File originalShaderJobFile = ns.get("shader");

    fileOps.copyShaderJobFileTo(originalShaderJobFile, interestingShaderJobFile, false);

    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
            fileOps.getFirstTwoLinesOfShader(interestingShaderJobFile, ShaderKind.FRAGMENT));

    ShaderJob initialState = fileOps.readShaderJobFile(interestingShaderJobFile);

    for (int i = 0; i < maxIterations; i++) {

      File workDir = Files.createDirectory(Paths.get("temp")).toFile();

      System.err.println("Trying iteration " + i);

      try {

        new ReductionDriver(
            new ReducerContext(
                reduceEverywhere,
                shadingLanguageVersion,
                generator,
                null,
                10,
                1, true),
            verbose,
            fileOps,
            initialState
        ).doReduction(
            FilenameUtils.removeExtension(interestingShaderJobFile.getAbsolutePath()),
            0,
            new RandomFileJudge(
                generator,
                10,
                ns.getBoolean("exception_on_invalid"), fileOps),
            workDir,
            STEP_LIMIT);

      } catch (Throwable throwable) {
        if (!throwable.toString().contains(expectedString)) {
          System.err.println("Exception does not contain required string:");
          System.err.println(throwable);
        } else {
          File[] files =
              fileOps.listShaderJobFiles(workDir, (dir, name) -> name.endsWith("success.info"));
          if (files.length == 0) {
            continue;
          }
          final File maxSuccess =
                Arrays.stream(files)
                      .max(Comparator.comparingInt(ReducerBugPoint::getStep)).get();

          fileOps.deleteShaderJobFile(interestingShaderJobFile);
          fileOps.copyShaderJobFileTo(maxSuccess, interestingShaderJobFile, true);

          initialState = fileOps.readShaderJobFile(interestingShaderJobFile);

          i = 0;
        }
      } finally {
        fileOps.deleteDirectory(workDir);
      }
    }
  }

  private static int getStep(File file) {
    final String[] split = file.getName().split("_");
    return Integer.parseInt(split[split.length - 2]);
  }

}
