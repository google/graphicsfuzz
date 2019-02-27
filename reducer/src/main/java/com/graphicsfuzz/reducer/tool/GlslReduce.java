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
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ReductionProgressHelper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.reducer.ReductionDriver;
import com.graphicsfuzz.reducer.ReductionKind;
import com.graphicsfuzz.reducer.filejudge.CustomFileJudge;
import com.graphicsfuzz.reducer.filejudge.FuzzingFileJudge;
import com.graphicsfuzz.reducer.filejudge.ImageGenErrorShaderFileJudge;
import com.graphicsfuzz.reducer.filejudge.ImageShaderFileJudge;
import com.graphicsfuzz.reducer.filejudge.ValidatorErrorShaderFileJudge;
import com.graphicsfuzz.reducer.reductionopportunities.ReducerContext;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.ImageComparisonMetric;
import com.graphicsfuzz.shadersets.ExactImageFileComparator;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import com.graphicsfuzz.shadersets.LocalShaderDispatcher;
import com.graphicsfuzz.shadersets.MetricImageFileComparator;
import com.graphicsfuzz.shadersets.RemoteShaderDispatcher;
import com.graphicsfuzz.util.ArgsUtil;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlslReduce {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlslReduce.class);

  public static final String METRICS_HELP_SHARED =
      "When reducing based on images being different, recommended related settings are:\n\n"
      + ImageComparisonMetric.FUZZY_DIFF
      + ": --reduction-kind ABOVE_THRESHOLD (provided threshold is ignored)\n\n"
      + ImageComparisonMetric.HISTOGRAM_CHISQR
      + ": --reduction-kind ABOVE_THRESHOLD --threshold 100.0\n\n"
      + ImageComparisonMetric.PSNR
      + ": --reduction-kind BELOW_THRESHOLD --threshold 30.0\n\n";

  private static ArgumentParser getParser() {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("glsl-reduce")
        .defaultHelp(true)
        .description("glsl-reduce takes a shader job `SHADER_JOB.json` "
            + "(a .json file alongside shader files with the same name, such as SHADER_JOB.frag "
            + "and/or SHADER_JOB.vert or SHADER_JOB.comp), "
            + "as well as further arguments or options to specify the interestingness test."
            + "glsl-reduce will try to simplify the given shader to a smaller,"
            + "simpler shader that is still deemed \"interesting\".");

    // Required arguments
    parser.addArgument("shader-job")
          .help("Path of shader job to be reduced.  E.g. /path/to/shaderjob.json ")
          .type(File.class);

    // Optional positional argument
    parser.addArgument("interestingness-test")
        .help("Path to an executable shell script that should decide whether a shader job is "
            + "interesting.  Only allowed (and then also required) when performing a custom "
            + "reduction, which is the default.")
        .nargs("?")
        .type(File.class);

    parser.addArgument("--reduction-kind")
          .help("Kind of reduction to be performed.  Options are:\n"
                + "   " + ReductionKind.CUSTOM
                + "             Reduces based on a user-supplied interestingness test.\n"
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
          .setDefault("CUSTOM")
          .type(String.class);

    parser.addArgument("--output")
        .help("Directory to which reduction intermediate and final results will be written.")
        .setDefault(new File("."))
        .type(File.class);

    parser.addArgument("--preserve-semantics")
        .help("Only perform semantics-preserving reductions.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max-steps")
        .help(
            "The maximum number of reduction steps to take before giving up and outputting the "
                + "final reduced file.")
        .setDefault(2000)
        .type(Integer.class);

    parser.addArgument("--verbose")
        .help("Emit detailed information related to the reduction process.")
        .action(Arguments.storeTrue());

    parser.addArgument("--seed")
        .help("Seed with which to initialize the random number generator that is used to control "
            + "reduction decisions.")
        .type(Integer.class);

    parser.addArgument("--timeout")
        .help(
            "Time in seconds after which checking interestingness of a shader job is aborted.")
        .setDefault(30)
        .type(Integer.class);

    parser.addArgument("--metric")
        .help("Metric used for image comparison.  Options are:\n"
            + "   " + ImageComparisonMetric.FUZZY_DIFF + "\n"
            + "   " + ImageComparisonMetric.HISTOGRAM_CHISQR + "\n"
            + "   " + ImageComparisonMetric.PSNR + "\n"
            + "\n" + METRICS_HELP_SHARED)
        .setDefault(ImageComparisonMetric.FUZZY_DIFF.toString())
        .type(String.class);

    parser.addArgument("--reference")
          .help("Path to reference .info.json result (with image result) for comparison.")
          .type(File.class);

    parser.addArgument("--threshold")
          .help("Threshold used for image comparison. See --metric for suggested values.")
          .setDefault(100.0)
          .type(Double.class);

    parser.addArgument("--retry-limit")
          .help("When getting an image via the server, the number of times the server should "
                + "allow the worker to retry a shader before assuming the shader crashes the "
                + "worker and marking it as SKIPPED.")
          .setDefault(2)
          .type(Integer.class);

    parser.addArgument("--skip-render")
          .help("Don't render (just compile) the shader on remote workers. "
              + "Useful when reducing compile or link errors.")
          .action(Arguments.storeTrue());

    parser.addArgument("--error-string")
          .help("For NO_IMAGE reductions, a shader is deemed interesting if this string is found "
              + "in the run log. E.g. \"Signal 11\".")
          .type(String.class);

    parser.addArgument("--server")
          .help("For non-CUSTOM reductions, shaders will be run via a worker connected to "
              + "this server URL.")
          .type(String.class);

    parser.addArgument("--worker")
          .help("For non-CUSTOM reductions, shaders will be run on this worker. "
              + "Use with --server.")
          .type(String.class);

    parser.addArgument("--stop-on-error")
          .help("Quit if something goes wrong during reduction; useful for testing.")
          .action(Arguments.storeTrue());

    parser.addArgument("--swiftshader")
          .help("Use swiftshader for rendering.")
          .action(Arguments.storeTrue());

    parser.addArgument("--continue-previous-reduction")
          .help("Carry on from where a previous reduction attempt left off.  Requires the "
              + "temporary files written by the previous reduction to be intact, including the "
              + "presence of a " + Constants.REDUCTION_INCOMPLETE + " file.")
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
      throws ArgumentParserException, IOException, ParseTimeoutException, InterruptedException,
      GlslParserException {

    ArgumentParser parser = getParser();

    Namespace ns = parser.parseArgs(args);

    final File workDir = ns.get("output");

    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    // Create output dir
    fileOps.mkdir(workDir);

    final File inputShaderJobFile = ns.get("shader_job");

    String shaderJobShortName = FilenameUtils.removeExtension(inputShaderJobFile.getName());

    try {
      if (ns.get("reduction_kind").equals(ReductionKind.VALIDATOR_ERROR)
            && ns.get("error_string") == null) {
        throw new ArgumentParserException(
              "If reduction kind is "
                    + ReductionKind.VALIDATOR_ERROR
                    + " then --error-string must be provided.",
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
      final int seed = ArgsUtil.getSeedArgument(ns);
      final String errorString = ns.get("error_string");
      final boolean reduceEverywhere = !ns.getBoolean("preserve_semantics");
      final boolean stopOnError = ns.get("stop_on_error");

      final String server = ns.get("server");
      final String worker = ns.get("worker");

      final boolean usingSwiftshader = ns.get("swiftshader");

      final boolean continuePreviousReduction = ns.get("continue_previous_reduction");

      if (managerOverride != null && (server == null || worker == null)) {
        throw new ArgumentParserException(
              "Must supply server (dummy string) and worker when executing in server process.",
              parser);
      }

      if (server != null && worker == null) {
        throw new ArgumentParserException("If --server is used then --worker is required", parser);
      }
      if (server == null && worker != null) {
        LOGGER.warn("Warning: --worker ignored, as it is used without --server");
      }
      if (server != null && usingSwiftshader) {
        LOGGER.warn("Warning: --swiftshader ignored, as --server is being used");
      }

      final File referenceResultFile = ns.get("reference");

      final File customJudgeScript = ns.get("interestingness_test");

      if (reductionKind == ReductionKind.CUSTOM) {
        if (server != null) {
          throwExceptionForCustomReduction("server");
        }
        if (worker != null) {
          throwExceptionForCustomReduction("worker");
        }
        if (errorString != null) {
          throwExceptionForCustomReduction("error-string");
        }
        if (referenceResultFile != null) {
          throwExceptionForCustomReduction("reference");
        }
        if (customJudgeScript == null) {
          throw new RuntimeException("A custom reduction requires an interestingness test to be "
              + "specified.");
        }
        if (!customJudgeScript.canExecute()) {
          throw new RuntimeException("Custom judge script must be executable.");
        }
      } else {
        if (customJudgeScript != null) {
          throw new RuntimeException("An interestingness test is only supported when a custom "
              + "reduction is used.");
        }
      }

      // Check input files
      fileOps.assertShaderJobRequiredFilesExist(inputShaderJobFile);
      if (referenceResultFile != null) {
        fileOps.doesShaderJobResultFileExist(referenceResultFile);
      }

      if (continuePreviousReduction) {
        fileOps.assertExists(new File(workDir, Constants.REDUCTION_INCOMPLETE));
      }

      // Copy input files to output dir.
      File copiedShaderJobFile = new File(workDir, inputShaderJobFile.getName());
      fileOps.copyShaderJobFileTo(inputShaderJobFile, copiedShaderJobFile, true);

      File referenceResultFileCopy = null;
      if (referenceResultFile != null) {
        referenceResultFileCopy = new File(workDir, "reference_image.info.json");
        fileOps.copyShaderJobResultFileTo(referenceResultFile, referenceResultFileCopy, true);
      }

      IFileJudge fileJudge;

      IShaderDispatcher imageGenerator =

            server == null || server.isEmpty() || server.equals(".")
                  ? new LocalShaderDispatcher(
                      usingSwiftshader,
                      fileOps,
                      new File(workDir, "temp"))
                  : new RemoteShaderDispatcher(
                      server + "/manageAPI",
                      worker,
                      managerOverride,
                      new AtomicLong(),
                      retryLimit);

      File corpus = new File(workDir, "corpus");

      switch (reductionKind) {
        case CUSTOM:
          fileJudge =
                new CustomFileJudge(customJudgeScript, workDir);
          break;
        case NO_IMAGE:
          fileJudge =
                new ImageGenErrorShaderFileJudge(
                      (errorString == null || errorString.isEmpty() ? null : errorString),
                      imageGenerator,
                      skipRender,
                      stopOnError, fileOps);
          break;
        case NOT_IDENTICAL:
          fileJudge = new ImageShaderFileJudge(
              referenceResultFileCopy,
              imageGenerator,
              stopOnError,
              new ExactImageFileComparator(false, fileOps),
              fileOps);
          break;
        case IDENTICAL:
          fileJudge = new ImageShaderFileJudge(
              referenceResultFileCopy,
              imageGenerator,
              stopOnError,
              new ExactImageFileComparator(true, fileOps),
              fileOps);
          break;
        case BELOW_THRESHOLD:
          fileJudge = new ImageShaderFileJudge(
              referenceResultFileCopy,
              imageGenerator,
              stopOnError,
              new MetricImageFileComparator(threshold, false, metric, fileOps),
              fileOps);
          break;
        case ABOVE_THRESHOLD:
          fileJudge = new ImageShaderFileJudge(
              referenceResultFileCopy,
              imageGenerator,
              stopOnError,
              new MetricImageFileComparator(threshold, true, metric, fileOps),
              fileOps);
          break;
        case VALIDATOR_ERROR:
          fileJudge = new ValidatorErrorShaderFileJudge(errorString.isEmpty() ? null : errorString);
          break;
        case ALWAYS_REDUCE:
          fileJudge = (shaderJobFile, shaderResultFile) -> true;
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
          inputShaderJobFile,
          shaderJobShortName,
          seed,
          fileJudge,
          workDir,
          maxSteps,
          reduceEverywhere,
          continuePreviousReduction,
          verbose,
          fileOps);

    } catch (Throwable throwable) {

      final File exceptionFile =
          ReductionProgressHelper.getReductionExceptionFile(workDir, shaderJobShortName);

      fileOps.writeStringToFile(
          exceptionFile,
          ExceptionUtils.getStackTrace(throwable)
      );

      throw throwable;
    }
  }

  public static void doReductionHelper(
      File initialShaderJobFile,
      String outputShortName,
      int seed,
      IFileJudge fileJudge,
      File workDir,
      int stepLimit,
      boolean reduceEverywhere,
      boolean continuePreviousReduction,
      boolean verbose,
      ShaderJobFileOperations fileOps)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final ShadingLanguageVersion shadingLanguageVersion =
        getGlslVersionForShaderJob(initialShaderJobFile, fileOps);
    final IRandom random = new RandomWrapper(seed);
    final IdGenerator idGenerator = new IdGenerator();

    final int fileCountOffset = getFileCountOffset(
        workDir,
        outputShortName,
        continuePreviousReduction,
        fileOps);
    final String startingShaderJobShortName = getStartingShaderJobShortName(
        workDir,
        outputShortName,
        continuePreviousReduction,
        fileOps);

    if (continuePreviousReduction) {
      fileOps.assertExists(new File(workDir, Constants.REDUCTION_INCOMPLETE));
      fileOps.deleteFile(new File(workDir, Constants.REDUCTION_INCOMPLETE));
    }

    final File shaderJobFile = new File(workDir, startingShaderJobShortName + ".json");
    final ShaderJob initialState =
        fileOps.readShaderJobFile(
            shaderJobFile
        );

    final boolean emitGraphicsFuzzDefines =
        fileOps.doesShaderJobUseGraphicsFuzzDefines(shaderJobFile);

    new ReductionDriver(
        new ReducerContext(
            reduceEverywhere,
            shadingLanguageVersion,
            random,
            idGenerator,
            emitGraphicsFuzzDefines),
        verbose,
        fileOps,
        initialState)
        .doReduction(
            outputShortName,
            fileCountOffset,
            fileJudge,
            workDir,
            stepLimit);
  }

  private static ShadingLanguageVersion getGlslVersionForShaderJob(
      File shaderFileJob,
      ShaderJobFileOperations fileOps)
      throws IOException {

    if (fileOps.doesShaderExist(shaderFileJob, ShaderKind.VERTEX)) {
      return ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
          fileOps.getFirstTwoLinesOfShader(shaderFileJob, ShaderKind.VERTEX));
    }
    if (fileOps.doesShaderExist(shaderFileJob, ShaderKind.FRAGMENT)) {
      return ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
          fileOps.getFirstTwoLinesOfShader(shaderFileJob, ShaderKind.FRAGMENT));
    }
    if (fileOps.doesShaderExist(shaderFileJob, ShaderKind.COMPUTE)) {
      return ShadingLanguageVersion.getGlslVersionFromFirstTwoLines(
          fileOps.getFirstTwoLinesOfShader(shaderFileJob, ShaderKind.COMPUTE));
    }
    throw new RuntimeException("Shader version not specified in any shader associated with"
        + "shader job " + shaderFileJob.getName());
  }

  private static String getStartingShaderJobShortName(
      File workDir,
      String shaderJobShortName,
      boolean continuePreviousReduction,
      ShaderJobFileOperations fileOps) throws IOException {

    if (!continuePreviousReduction) {
      return shaderJobShortName;
    }
    final int latestSuccessfulReduction =
        ReductionProgressHelper
            .getLatestReductionStepSuccess(workDir, shaderJobShortName, fileOps)
            .orElse(0);
    if (latestSuccessfulReduction == 0) {
      return shaderJobShortName;
    }
    return ReductionDriver.getReductionStepShaderJobShortName(
        shaderJobShortName,
        latestSuccessfulReduction, Optional.of("success"));
  }

  private static int getFileCountOffset(File workDir, String shaderJobShortName,
        boolean continuePreviousReduction, ShaderJobFileOperations fileOps) throws IOException {
    if (!continuePreviousReduction) {
      return 0;
    }
    return ReductionProgressHelper.getLatestReductionStepAny(
        workDir,
        shaderJobShortName,
        fileOps).orElse(0);
  }

  private static void throwExceptionForCustomReduction(String option) {
    throw new RuntimeException("The '--" + option + "' option is not compatible with a custom "
        + "reduction; details of judgement should all be captured in the interestingness test.");
  }

}
