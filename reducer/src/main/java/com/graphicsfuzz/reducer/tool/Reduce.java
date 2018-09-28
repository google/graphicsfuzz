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
import com.graphicsfuzz.common.transformreduce.Constants;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.FileHelper;
import com.graphicsfuzz.common.util.Helper;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ReductionProgressHelper;
import com.graphicsfuzz.common.util.UniformsInfo;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.ReductionKind;
import com.graphicsfuzz.reducer.filejudge.FuzzingFileJudge;
import com.graphicsfuzz.reducer.filejudge.ImageGenErrorShaderFileJudge;
import com.graphicsfuzz.reducer.filejudge.ImageShaderFileJudge;
import com.graphicsfuzz.reducer.filejudge.ValidatorErrorShaderFileJudge;
import com.graphicsfuzz.reducer.reductionopportunities.ReductionOpportunityContext;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.ImageComparisonMetric;
import com.graphicsfuzz.shadersets.ExactImageFileComparator;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.LocalShaderDispatcher;
import com.graphicsfuzz.shadersets.MetricImageFileComparator;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reduce {

  private static final Logger LOGGER = LoggerFactory.getLogger(Reduce.class);

  private static ArgumentParser getParser() {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("Reduce")
          .defaultHelp(true)
          .description("Reduce a shader job, driven by a criterion of interest.");

    // Required arguments
    parser.addArgument("shader_job")
          .help("Path of shader job to be reduced.  If path is /path/to/p, shaders and meta data "
              + "to be reduced will be /path/to/p.frag, /path/to/p.vert, /path/to/p.json, etc., "
              + "and reduction temporaries will include 'p' in their name.")
          .type(String.class);

    parser.addArgument("reduction_kind")
          .help("Kind of reduction to be performed.  Options are:\n"
                + "   " + ReductionKind.NO_IMAGE
                + "               Reduces while image generation fails to produce an image.\n"
                + "   " + ReductionKind.NOT_IDENTICAL
                + "       Reduces while produced image is not identical to reference.\n"
                + "   " + ReductionKind.IDENTICAL
                + "           Reduces while produced image is identical to reference.\n"
                + "   " + ReductionKind.BELOW_THRESHOLD
                + "     Reduces while histogram difference between produced image and "
                + "reference is below a threshold.\n"
                + "   " + ReductionKind.ABOVE_THRESHOLD
                + "     Reduces while histogram difference between produced image and "
                + "reference is above a threshold.\n"
                + "   " + ReductionKind.VALIDATOR_ERROR
                + "     Reduces while validator gives a particular error\n"
                + "   " + ReductionKind.ALWAYS_REDUCE
                + "       Always reduces (useful for testing)\n")
          .type(String.class);

    parser.addArgument("--metric")
        .help("Metric to be used for image comparison.  Options are:\n"
            + "   " + ImageComparisonMetric.HISTOGRAM_CHISQR + "\n"
            + "   " + ImageComparisonMetric.PSNR + "\n")
        .setDefault(ImageComparisonMetric.HISTOGRAM_CHISQR.toString())
        .type(String.class);

    parser.addArgument("--reference_image")
          .help("Path to reference image for comparisons.")
          .type(File.class);

    parser.addArgument("--threshold")
          .help("Threshold used for histogram differencing.")
          .setDefault(100.0)
          .type(Double.class);

    parser.addArgument("--timeout")
          .help(
                "Time in seconds after which execution of an individual variant is terminated "
                      + "during reduction.")
          .setDefault(30)
          .type(Integer.class);

    parser.addArgument("--max_steps")
          .help(
                "The maximum number of reduction steps to take before giving up and outputting the "
                      + "final reduced file.")
          .setDefault(250)
          .type(Integer.class);

    parser.addArgument("--retry_limit")
          .help("When getting an image via the server, the number of times the server should "
                + "allow the client to retry a shader before assuming the shader crashes the "
                + "client and marking it as SKIPPED.")
          .setDefault(2)
          .type(Integer.class);

    parser.addArgument("--verbose")
          .help("Emit detailed information related to the reduction process.")
          .action(Arguments.storeTrue());

    parser.addArgument("--skip_render")
          .help("Don't render the shader on remote clients. Useful when reducing compile or link "
                + "errors.")
          .action(Arguments.storeTrue());

    parser.addArgument("--seed")
          .help("Seed to initialize random number generator with.")
          .setDefault(new Random().nextInt())
          .type(Integer.class);

    parser.addArgument("--error_string")
          .help("String checked for containment in validation or compilation tool error message.")
          .type(String.class);

    parser.addArgument("--server")
          .help("Server URL to which image jobs are sent.")
          .type(String.class);

    parser.addArgument("--token")
          .help("Client token to which image jobs are sent. Used with --server.")
          .type(String.class);

    parser.addArgument("--output")
          .help("Output directory.")
          .setDefault(new File("."))
          .type(File.class);

    parser.addArgument("--reduce_everywhere")
          .help("Allow reducer to reduce arbitrarily.")
          .action(Arguments.storeTrue());

    parser.addArgument("--stop_on_error")
          .help("Quit if something goes wrong during reduction; useful for testing.")
          .action(Arguments.storeTrue());

    parser.addArgument("--swiftshader")
          .help("Use swiftshader for rendering.")
          .action(Arguments.storeTrue());

    parser.addArgument("--continue_previous_reduction")
          .help("Carry on from where a previous reduction attempt left off.")
          .action(Arguments.storeTrue());

    return parser;

  }

  public static void main(String[] args) {
    try {
      mainHelper(args, null);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Throwable ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public static void mainHelper(
        String[] args,
        FuzzerServiceManager.Iface managerOverride)
        throws ArgumentParserException, IOException, ParseTimeoutException {

    ArgumentParser parser = getParser();

    Namespace ns = parser.parseArgs(args);

    final File workDir = ns.get("output");

    // Create output dir
    FileUtils.forceMkdir(workDir);

    final String pathToShaderJob = ns.get("shader_job");
    final String shaderJobShortName = FilenameUtils.getBaseName(pathToShaderJob);

    try {
      if (ns.get("reduction_kind").equals(ReductionKind.VALIDATOR_ERROR)
            && ns.get("error_string") == null) {
        throw new ArgumentParserException(
              "If reduction kind is "
                    + ReductionKind.VALIDATOR_ERROR
                    + " then --error_string must be provided.",
              parser);
      }

      ReductionKind reductionKind = null;
      try {
        reductionKind = ReductionKind.valueOf(((String) ns.get("reduction_kind")).toUpperCase());
      } catch (IllegalArgumentException exception) {
        throw new ArgumentParserException(
              "unknown reduction kind argument found: " + ns.get("reduction_kind"),
              parser);
      }

      ImageComparisonMetric metric = null;
      try {
        metric = ImageComparisonMetric.valueOf(((String) ns.get("metric")).toUpperCase());
      } catch (IllegalArgumentException exception) {
        throw new ArgumentParserException(
            "unknown metric argument found: " + ns.get("metric"),
            parser);
      }

      final double threshold = ns.get("threshold");
      // TODO: integrate timeout into reducer
      @SuppressWarnings("UnusedAssignment") Integer timeout = ns.get("timeout");
      final Integer maxSteps = ns.get("max_steps");
      final Integer retryLimit = ns.get("retry_limit");
      final Boolean verbose = ns.get("verbose");
      final boolean skipRender = ns.get("skip_render");
      final int seed = ns.get("seed");
      final String errorString = ns.get("error_string");
      final boolean reduceEverywhere = ns.get("reduce_everywhere");
      final boolean stopOnError = ns.get("stop_on_error");

      final String server = ns.get("server");
      final String token = ns.get("token");

      final Boolean usingSwiftshader = ns.get("swiftshader");

      final Boolean continuePreviousReduction = ns.get("continue_previous_reduction");

      if (managerOverride != null && (server == null || token == null)) {
        throw new ArgumentParserException(
              "Must supply server (dummy string) and token when executing in server process.",
              parser);
      }

      if (server != null && token == null) {
        throw new ArgumentParserException("If --server is used then --token is required", parser);
      }
      if (server == null && token != null) {
        LOGGER.warn("Warning: --token ignored, as it is used without --server");
      }
      if (server != null && usingSwiftshader) {
        LOGGER.warn("Warning: --swiftshader ignored, as --server is being used");
      }

      final File reductionReferenceImage = ns.get("reference_image");
      final File shaderJobVertex = new File(pathToShaderJob + ".vert");
      final File shaderJobFragment = new File(pathToShaderJob + ".frag");
      final File shaderJobJson = new File(pathToShaderJob + ".json");

      // Check input files
      if (!shaderJobVertex.isFile() && !shaderJobFragment.isFile()) {
        throw new FileNotFoundException("Cannot find vertex or fragment shader at "
            + pathToShaderJob + ".[vert/frag]");
      }
      FileHelper.checkExists(shaderJobJson);
      FileHelper.checkExistsOrNull(reductionReferenceImage);
      if (continuePreviousReduction) {
        FileHelper.checkExists(new File(workDir, Constants.REDUCTION_INCOMPLETE));
      }

      // Copy input files to output dir, unless they are already there.  The case of them being
      // already there is legitimate when the working directory is the current directory.
      copyFileToWorkDirIfNeeded(shaderJobVertex, workDir, shaderJobShortName + ".vert");
      copyFileToWorkDirIfNeeded(shaderJobFragment, workDir, shaderJobShortName + ".frag");
      copyFileToWorkDirIfNeeded(shaderJobJson, workDir, shaderJobShortName + ".json");

      if (reductionReferenceImage != null) {
        FileUtils.copyFile(reductionReferenceImage,
            ImageShaderFileJudge.getReferenceImageInWorkDir(workDir));
      }

      IFileJudge fileJudge;

      IShaderDispatcher imageGenerator =

            server == null || server.isEmpty() || server.equals(".")
                  ? new LocalShaderDispatcher(usingSwiftshader)
                  : new RemoteShaderDispatcher(
                        server + "/manageAPI",
                        token,
                        managerOverride,
                        new AtomicLong(),
                        retryLimit);

      File corpus = new File(workDir, "corpus");

      switch (reductionKind) {
        case NO_IMAGE:
          fileJudge =
                new ImageGenErrorShaderFileJudge(
                      (errorString == null || errorString.isEmpty()) ? null
                            : Pattern.compile(".*" + errorString + ".*", Pattern.DOTALL),
                      imageGenerator,
                      skipRender,
                      stopOnError);
          break;
        case NOT_IDENTICAL:
          fileJudge = new ImageShaderFileJudge(
                new ExactImageFileComparator(false),
                imageGenerator,
                stopOnError);
          break;
        case IDENTICAL:
          fileJudge = new ImageShaderFileJudge(
                new ExactImageFileComparator(true),
                imageGenerator,
                stopOnError);
          break;
        case BELOW_THRESHOLD:
          fileJudge = new ImageShaderFileJudge(
                new MetricImageFileComparator(threshold, false, metric),
                imageGenerator,
                stopOnError);
          break;
        case ABOVE_THRESHOLD:
          fileJudge = new ImageShaderFileJudge(
                new MetricImageFileComparator(threshold, true, metric),
                imageGenerator,
                stopOnError);
          break;
        case VALIDATOR_ERROR:
          fileJudge = new ValidatorErrorShaderFileJudge(errorString.isEmpty() ? null
                : Pattern.compile(".*" + errorString + ".*", Pattern.DOTALL));
          break;
        case ALWAYS_REDUCE:
          fileJudge = (shaderWorkDir, shaderPrefix) -> true;
          break;
        case FUZZ:
          fileJudge = new FuzzingFileJudge(corpus, imageGenerator);
          break;
        default:
          throw new ArgumentParserException(
                "Unsupported reduction kind: " + reductionKind,
                parser);
      }

      doReductionHelper(
          shaderJobShortName,
          seed,
          fileJudge,
          workDir,
          maxSteps,
          reduceEverywhere,
          continuePreviousReduction,
          verbose);

    } catch (Throwable ex) {

      final File exceptionFile =
          ReductionProgressHelper.getReductionExceptionFile(workDir, shaderJobShortName);

      FileUtils.writeStringToFile(
            exceptionFile,
            ExceptionUtils.getStackTrace(ex),
            Charset.defaultCharset()
      );

      throw ex;
    }
  }

  private static void copyFileToWorkDirIfNeeded(File srcFile, File targetDir, String dstName)
      throws IOException {
    assert targetDir.isDirectory();
    final File dstFile = new File(targetDir, dstName);
    if (srcFile.exists() && !dstFile.exists()) {
      FileUtils.copyFile(srcFile, dstFile);
    }
  }

  public static void doReductionHelper(
      String shaderJobShortName,
      int seed,
      IFileJudge fileJudge,
      File workDir,
      int stepLimit,
      boolean reduceEverywhere,
      boolean continuePreviousReduction,
      boolean verbose)
      throws IOException, ParseTimeoutException {
    final ShadingLanguageVersion shadingLanguageVersion =
        getGlslVersionForShaderJob(workDir, shaderJobShortName);
    final IRandom random = new RandomWrapper(seed);
    final IdGenerator idGenerator = new IdGenerator();

    final int fileCountOffset = getFileCountOffset(workDir, shaderJobShortName,
        continuePreviousReduction);
    final String startingShaderJobShortName = getStartingShaderJobShortName(workDir,
        shaderJobShortName,
        continuePreviousReduction);

    if (continuePreviousReduction) {
      assert new File(workDir, Constants.REDUCTION_INCOMPLETE).exists();
      new File(workDir, Constants.REDUCTION_INCOMPLETE).delete();
    }

    ShaderJob initialState = createInitialReductionState(workDir, startingShaderJobShortName);

    new ReductionDriver(
        new ReductionOpportunityContext(
            reduceEverywhere,
            shadingLanguageVersion,
            random,
            idGenerator),
        verbose,
        initialState)
        .doReduction(shaderJobShortName,
            fileCountOffset,
            fileJudge,
            workDir,
            stepLimit);
  }

  private static ShaderJob createInitialReductionState(File workDir, String shaderJobShortName)
      throws IOException, ParseTimeoutException {
    final File vertexShader = new File(workDir,shaderJobShortName + ".vert");
    final File fragmentShader = new File(workDir,shaderJobShortName + ".frag");
    final File json = new File(workDir,shaderJobShortName + ".json");
    return new GlslShaderJob(
        maybeParseShader(vertexShader),
        maybeParseShader(fragmentShader),
        new UniformsInfo(json));
  }

  private static Optional<TranslationUnit> maybeParseShader(File shader)
      throws IOException, ParseTimeoutException {
    return shader.isFile()
        ? Optional.of(Helper.parse(shader, true))
        : Optional.empty();
  }

  private static ShadingLanguageVersion getGlslVersionForShaderJob(File workDir,
                                                                   String shaderJobShortName)
      throws IOException {
    final File vertexShader = new File(workDir,shaderJobShortName + ".vert");
    if (vertexShader.isFile()) {
      return ShadingLanguageVersion.getGlslVersionFromShader(vertexShader);
    }
    final File fragmentShader = new File(workDir, shaderJobShortName + ".frag");
    if (fragmentShader.isFile()) {
      return ShadingLanguageVersion.getGlslVersionFromShader(fragmentShader);
    }
    throw new RuntimeException("Shader version not specified in vertex or fragment shader.");
  }

  private static String getStartingShaderJobShortName(File workDir, String shaderJobShortName,
        boolean continuePreviousReduction) {
    if (!continuePreviousReduction) {
      return shaderJobShortName;
    }
    final int latestSuccessfulReduction = ReductionProgressHelper
          .getLatestReductionStepSuccess(workDir,
                shaderJobShortName).orElse(0);
    if (latestSuccessfulReduction == 0) {
      return shaderJobShortName;
    }
    return ReductionDriver.getReductionStepShaderJobShortName(
        shaderJobShortName,
        latestSuccessfulReduction, Optional.of("success"));
  }

  private static int getFileCountOffset(File workDir, String shaderJobShortName,
        boolean continuePreviousReduction) {
    if (!continuePreviousReduction) {
      return 0;
    }
    return ReductionProgressHelper.getLatestReductionStepAny(workDir,
          shaderJobShortName).orElse(0);
  }

}
