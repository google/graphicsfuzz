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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.util.ToolPaths;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateShaderFamily {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenerateShaderFamily.class);

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("GenerateShaderFamily")
        .defaultHelp(true)
        .description("Generate a family of equivalent shader jobs from a reference shader job.");

    // Required arguments
    parser.addArgument("reference-shader-job")
        .help("Input reference shader job JSON file.")
        .type(File.class);

    parser.addArgument("donors")
        .help("Path to directory of donor shaders.")
        .type(File.class);

    parser.addArgument("glsl-version")
        .help("Version of GLSL to target.")
        .type(String.class);

    parser.addArgument("output-dir")
        .help("Directory in which to store output shaders.")
        .type(File.class);

    Generate.addGeneratorCommonArguments(parser);

    addFamilyGenerationArguments(parser);

    parser.addArgument("--num-variants")
        .help("Number of variants to produce.")
        .type(Integer.class)
        .setDefault(10);

    return parser.parseArgs(args);

  }

  static void addFamilyGenerationArguments(ArgumentParser parser) {
    parser.addArgument("--disable-validator")
        .help("Disable calling validation tools on generated variants.")
        .action(Arguments.storeTrue());

    parser.addArgument("--keep-bad-variants")
        .help("Do not remove invalid variants upon generation.")
        .action(Arguments.storeTrue());

    parser.addArgument("--stop-on-fail")
        .help("Quit if an invalid variant is generated.")
        .action(Arguments.storeTrue());

    parser.addArgument("--verbose")
        .help("Emit detailed information regarding the progress of the generation.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max-bytes")
        .help("Maximum allowed size, in bytes, for variant shader.")
        .setDefault(500000)
        .type(Integer.class);

    parser.addArgument("--max-factor")
        .help("Maximum blowup allowed, compared with size of reference shader (default: no limit).")
        .type(Float.class);

    parser.addArgument("--require-license")
        .help("Require a license file to be provided alongside the reference and pass details "
            + "through to generated shaders.")
        .action(Arguments.storeTrue());
  }

  public static void mainHelper(String[] args) throws ArgumentParserException,
      InterruptedException, IOException, ParseTimeoutException {

    Namespace ns = parse(args);

    final String glslVersion = ns.getString("glsl_version");
    final File referenceShaderJob = ns.get("reference_shader_job");
    final File donorsDir = ns.get("donors");
    final File outputDir = ns.get("output_dir") == null ? new File(".") : ns.get("output_dir");
    final boolean webgl = ns.getBoolean("webgl");
    final boolean verbose = ns.getBoolean("verbose");
    final boolean disableValidator = ns.getBoolean("disable_validator");
    final boolean writeProbabilities = ns.getBoolean("write_probabilities");
    final boolean keepBadVariants = ns.getBoolean("keep_bad_variants");
    final boolean stopOnFail = ns.getBoolean("stop_on_fail");
    final int seed = ns.getInt("seed");
    final int numVariants = ns.getInt("num_variants");
    Optional<Integer> maxBytes = ns.get("max_bytes") == null ? Optional.empty() :
        Optional.of(ns.getInt("max_bytes"));
    Optional<Float> maxFactor = ns.get("max_factor") == null ? Optional.empty() :
        Optional.of(ns.getFloat("max_factor"));
    final GeneratorArguments generatorArguments = Generate.getGeneratorArguments(ns);

    if (webgl && !ShadingLanguageVersion.isWebGlCompatible(glslVersion)) {
      // Check immediately that if --webgl has been passed, it is possible to get the given
      // WebGL shading language version as a string.
      throw new RuntimeException("Given GLSL version " + glslVersion + " is not WebGL-compatible.");
    }

    if (verbose) {
      LOGGER.info("Using seed " + seed);
    }

    if (!referenceShaderJob.isFile()) {
      throw new FileNotFoundException("Reference shader job " + referenceShaderJob.getAbsolutePath()
          + " does not exist.");
    }

    if (verbose) {
      LOGGER.info("Using donor folder " + donorsDir.getAbsolutePath());
    }

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    fileOps.forceMkdir(outputDir);

    // Prepare reference shaders.
    final File preparedReferenceShaderJob = new File(outputDir, "reference.json");
    PrepareReference.prepareReference(referenceShaderJob,
        preparedReferenceShaderJob,
        ShadingLanguageVersion.fromVersionString(glslVersion),
        generatorArguments.getReplaceFloatLiterals(),
        // We subtract 1 because we need to be able to add injectionSwitch
        generatorArguments.getMaxUniforms() - 1,
        generatorArguments.getGenerateUniformBindings(),
        fileOps);

    // Validate reference shaders.
    if (!disableValidator) {
      if (!(fileOps.areShadersValid(preparedReferenceShaderJob, false)
          && fileOps.areShadersValidShaderTranslator(preparedReferenceShaderJob, false))) {
        throw new RuntimeException("One or more of the prepared shaders of shader job "
            + preparedReferenceShaderJob.getAbsolutePath() + " is not valid.");
      }
    }

    if (primitivesFile(referenceShaderJob).isFile()) {
      FileUtils.copyFile(primitivesFile(referenceShaderJob),
          new File(outputDir, "reference.primitives"));
      final JsonObject primitivesJson =
          new Gson().fromJson(new FileReader(primitivesFile(referenceShaderJob)),
              JsonObject.class);
      if (primitivesJson.has("texture")) {
        final String textureFilename = primitivesJson.get("texture").getAsString();
        FileUtils.copyFile(new File(textureFilename), new File(outputDir, textureFilename));
      }
    }

    int generatedVariants = 0;
    int triedVariants = 0;

    final IRandom generator = new RandomWrapper(seed);

    // Main variant generation loop
    while (generatedVariants < numVariants) {
      if (verbose) {
        LOGGER.info("Trying variant " + triedVariants + " (produced " + generatedVariants
            + " of " + numVariants + ")");
      }
      // Generate a variant
      final int innerSeed = generator.nextInt(Integer.MAX_VALUE);
      if (verbose) {
        LOGGER.info("Generating variant with inner seed " + innerSeed);
      }
      final File variantShaderJobFile = new File(outputDir, "variant_" + String.format("%03d",
          generatedVariants) + ".json");

      triedVariants++;
      try {
        Generate.generateVariant(fileOps, referenceShaderJob, variantShaderJobFile,
            generatorArguments, innerSeed, writeProbabilities);
      } catch (Exception exception) {
        if (verbose) {
          LOGGER.error("Failed generating variant:");
          LOGGER.error(exception.getMessage());
          exception.printStackTrace();
        }
        if (stopOnFail) {
          final String message = "Failed generating a variant, stopping.";
          LOGGER.info(message);
          throw new RuntimeException(message);
        }
        continue;
      }

      // Check the shader is valid
      if (skipDueToInvalidShader(fileOps, variantShaderJobFile, disableValidator,
          keepBadVariants, stopOnFail)) {
        continue;
      }

      // Check code size
      if (generatedShadersTooLarge(fileOps,
          preparedReferenceShaderJob,
          variantShaderJobFile,
          maxFactor,
          maxBytes,
          verbose)) {
        // A generated shader is too large - discard it (but don't log it as bad)
        continue;
      }

      if (primitivesFile(preparedReferenceShaderJob).isFile()) {
        FileUtils.copyFile(primitivesFile(preparedReferenceShaderJob),
            primitivesFile(variantShaderJobFile));
      }

      final int chunkSize = 4;
      generatedVariants++;
      if ((generatedVariants % chunkSize) == 0) {
        LOGGER.info("Done " + (100f * (float) generatedVariants / (float) numVariants) + "%");
      }
    }

    // Produce JSON file recording some info on what was generated.
    final JsonObject infoLog = new JsonObject();
    final File hashFile = new File(new File(ToolPaths.getInstallDirectory()), "HASH");
    final String hashContents = hashFile.isFile() ? FileUtils.readFileToString(hashFile,
        StandardCharsets.UTF_8) : "none";
    infoLog.addProperty("git_hash", hashContents);
    infoLog.addProperty("args", String.join(" ", args));
    infoLog.addProperty("seed", seed);

    // Pretty-print the info log.
    FileUtils.writeStringToFile(new File(outputDir,"infolog.json"),
        new GsonBuilder().setPrettyPrinting().create()
            .toJson(infoLog), StandardCharsets.UTF_8);

    LOGGER.info("Generation complete -- generated " + generatedVariants + " variants in "
            + triedVariants + " tries.");
  }

  private static File primitivesFile(File shaderJob) {
    return new File(shaderJob.getParentFile(),
        FilenameUtils.removeExtension(shaderJob.getName()) + ".primitives");
  }

  private static boolean generatedShadersTooLarge(ShaderJobFileOperations fileOps,
                                                  File preparedReferenceShaderJobFile,
                                                  File variantShaderJobFile,
                                                  Optional<Float> maxFactor,
                                                  Optional<Integer> maxBytes,
                                                  boolean verbose) {
    // Go through all the shader kinds.
    for (ShaderKind shaderKind : ShaderKind.values()) {
      // Consider only those shader kinds that exist in the prepared reference shader job,
      // and which should also exist in the variant shader job.
      if (fileOps.doesShaderExist(preparedReferenceShaderJobFile, shaderKind)) {
        assert fileOps.doesShaderExist(variantShaderJobFile, shaderKind);
        final long numBytesReference = fileOps.getShaderLength(preparedReferenceShaderJobFile,
            shaderKind);
        final long numBytesVariant = fileOps.getShaderLength(variantShaderJobFile,
            shaderKind);

        final String logMessagePrefix = "Discarding " + shaderKind.getFileExtension()
            + " shader of size " + numBytesVariant + " bytes; ";

        if (maxFactor.isPresent() && numBytesVariant > maxFactor.get() * numBytesReference) {
          if (verbose) {
            LOGGER.info(logMessagePrefix + "more than " + maxFactor.get() + " times larger "
                    + "than reference of size " + numBytesReference + " bytes");
          }
          return true;
        }

        if (maxBytes.isPresent() && numBytesVariant > maxBytes.get()) {
          if (verbose) {
            LOGGER.info(logMessagePrefix + "exceeds limit of " + maxBytes.get() + " bytes");
          }
          return true;
        }
      }
    }
    return false;
  }

  private static boolean skipDueToInvalidShader(ShaderJobFileOperations fileOps,
                                                File variantShaderJobFile,
                                                boolean disableValidator,
                                                boolean keepBadVariants,
                                                boolean stopOnFail)
      throws IOException, InterruptedException {

    if (disableValidator) {
      // Validation has been disabled, so don't do any skipping.
      return false;
    }

    if (!(fileOps.areShadersValid(variantShaderJobFile, false)
        && fileOps.areShadersValidShaderTranslator(variantShaderJobFile, false))) {
      if (keepBadVariants) {
        fileOps.moveShaderJobFileTo(variantShaderJobFile,
            new File(variantShaderJobFile.getParentFile(),
                "bad_" + variantShaderJobFile.getName()), true);
      } else {
        fileOps.deleteShaderJobFile(variantShaderJobFile);
      }
      if (stopOnFail) {
        final String message = "Generated an invalid variant, stopping.";
        LOGGER.error(message);
        throw new RuntimeException(message);
      }
      return true;
    }
    return false;
  }

  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Throwable ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

}
