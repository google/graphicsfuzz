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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.reducer.IReductionStateFileWriter;
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.glslreducers.GlslReductionState;
import com.graphicsfuzz.reducer.glslreducers.GlslReductionStateFileWriter;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

public class ReducerBugPoint {

  private static final int STEP_LIMIT = 50;

  private static Namespace parse(String[] args) {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("ReducerBugPoint")
          .defaultHelp(true)
          .description("Find bugs in the reducer.");

    // Required arguments
    parser.addArgument("shader")
          .help("Path of shader to be analysed.")
          .type(File.class);

    parser.addArgument("--output")
          .help("Output directory.")
          .setDefault(new File("."))
          .type(File.class);

    // Optional arguments
    parser.addArgument("--max_iterations")
          .help("Maximum number of times to iterate before giving up.")
          .setDefault(30)
          .type(Integer.class);

    parser.addArgument("--seed")
          .help("Seed to initialize random number generator with.")
          .setDefault(new Random().nextInt())
          .type(Integer.class);

    parser.addArgument("--reduce_everywhere")
          .help("Reduce arbitrary parts of the shader.")
          .action(Arguments.storeTrue());

    parser.addArgument("--verbose")
          .help("Emit verbose info.")
          .action(Arguments.storeTrue());

    parser.addArgument("--exception_on_invalid")
          .help("Throw exception when shader is invalid.")
          .action(Arguments.storeTrue());

    parser.addArgument("--expected_string")
          .help("A string to look for in the exception message.")
          .type(String.class);

    try {
      return parser.parseArgs(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
      return null;
    }

  }


  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException {

    final Namespace ns = parse(args);

    final int seed = ns.get("seed");

    final int maxIterations = ns.get("max_iterations");

    final boolean reduceEverywhere = ns.get("reduce_everywhere");

    final boolean verbose = ns.get("verbose");

    final String expectedString = ns.getString("expected_string") == null
          ? ""
          : ns.getString("expected_string");

    final IRandom generator = new RandomWrapper(seed);

    final File interestingFile = new File("interesting.frag");
    final File interestingJson = new File(Helper.jsonFilenameForShader(
          interestingFile.getAbsolutePath()));
    if (interestingFile.exists()) {
      interestingFile.delete();
    }
    if (interestingJson.exists()) {
      interestingJson.delete();
    }

    final File originalShaderFile = ns.get("shader");
    FileUtils.copyFile(originalShaderFile, interestingFile);
    FileUtils.copyFile(new File(Helper.jsonFilenameForShader(originalShaderFile.getAbsolutePath())),
          interestingJson);

    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromShader(interestingFile);

    TranslationUnit interestingTranslationUnit = Helper.parse(interestingFile, true);

    for (int i = 0; i < maxIterations; i++) {

      File workDir = Files.createDirectory(Paths.get("temp")).toFile();

      System.err.println("Trying iteration " + i);

      GlslReductionState initialState = new GlslReductionState(
            Optional.of(interestingTranslationUnit));
      IReductionStateFileWriter fileWriter = new GlslReductionStateFileWriter(
          shadingLanguageVersion);

      try {

        new ReductionDriver(new ReductionOpportunityContext(
              reduceEverywhere,
            shadingLanguageVersion,
              generator,
              null,
              10,
              1), verbose, initialState)
              .doReduction(FilenameUtils.removeExtension(interestingFile.getAbsolutePath()), 0,
                    fileWriter,
                    new RandomFileJudge(generator, 10,
                          ns.getBoolean("exception_on_invalid")),
                    workDir,
                    STEP_LIMIT);

      } catch (Throwable throwable) {
        if (!throwable.toString().contains(expectedString)) {
          System.err.println("Exception does not contain required string:");
          System.err.println(throwable);
        } else {
          final FileFilter fileFilter = new WildcardFileFilter("*success.frag");
          File[] files = workDir.listFiles(fileFilter);
          if (files.length == 0) {
            continue;
          }
          final File maxSuccess =
                Arrays.stream(files)
                      .max((item1, item2) -> Integer.compare(getStep(item1), getStep(item2))).get();

          FileUtils.copyFile(maxSuccess, interestingFile);
          interestingJson.delete();
          FileUtils.copyFile(new File(Helper.jsonFilenameForShader(maxSuccess.getAbsolutePath())),
                interestingJson);

          interestingTranslationUnit = Helper.parse(interestingFile, true);

          i = 0;
        }
      } finally {
        FileUtils.deleteDirectory(workDir);
      }
    }
  }

  private static int getStep(File file) {
    final String[] split = file.getName().split("_");
    return Integer.parseInt(split[split.length - 2]);
  }

}
