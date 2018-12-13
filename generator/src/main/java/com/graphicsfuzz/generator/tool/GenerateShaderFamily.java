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

import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.common.util.ShaderKind;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
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
        .help("Path of folder of donor shaders.")
        .type(File.class);

    parser.addArgument("glsl-version")
        .help("Version of GLSL to target.")
        .type(String.class);

    parser.addArgument("output-dir")
        .help("Directory in which to store output shaders.")
        .type(File.class);

    Generate.addGeneratorCommonArguments(parser);

    parser.addArgument("--num-variants")
        .help("Number of variants to produce.")
        .type(Integer.class)
        .setDefault(10);

    parser.addArgument("--hash_file")
        .help("Path to file containing git hash.")
        .type(File.class)
        .setDefault("TODO");

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
        .help("Maximum allowed size, in bytes, for variant shader (default: no limit).")
        .type(Integer.class);

    parser.addArgument("--max-factor")
        .help("Maximum blowup allowed, compared with size of reference shader (default: no limit).")
        .type(Float.class);

    parser.addArgument("--require-license")
        .help("Require a license file to be provided alongside the reference and pass details "
            + "through to generated shaders.")
        .action(Arguments.storeTrue());

    return parser.parseArgs(args);

  }

  public static void mainHelper(String[] args) throws ArgumentParserException,
      InterruptedException, IOException, ParseTimeoutException {

    Namespace ns = parse(args);

    final int seed = ns.getInt("seed");
    final boolean verbose = ns.getBoolean("verbose");
    if (verbose) {
      LOGGER.info("Using seed " + seed);
    }

    final File referenceShaderJob = ns.get("reference_shader_job");
    if (!referenceShaderJob.isFile()) {
      throw new FileNotFoundException("Reference shader job " + referenceShaderJob.getAbsolutePath()
          + " does not exist.");
    }

    final File donorsDir = ns.get("donors");
    if (verbose) {
      LOGGER.info("Using donor folder " + donorsDir.getAbsolutePath());
    }

    final File outputDir = ns.get("output_dir") == null ? new File(".") : ns.get("output_dir");
    if (outputDir.isDirectory()) {
      LOGGER.info("Overwriting previous output directory (" + outputDir.getAbsolutePath() + ")");
    }

    FileUtils.deleteDirectory(outputDir);
    if (!outputDir.mkdir()) {
      throw new IOException("Problem creating output directory (" + outputDir.getAbsolutePath()
          + ")");
    }

    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    // Prepare reference shaders.
    final File preparedReferenceShaderJob = new File(outputDir, "reference.json");
    PrepareReference.prepareReference(referenceShaderJob,
        preparedReferenceShaderJob,
        ShadingLanguageVersion.fromVersionString(ns.getString("glsl_version")),
        ns.getBoolean("replace_float_literals"),
        ns.getInt("max_uniforms") - 1, // We subtract 1 because we need to be able to add injectionSwitch
        ns.getBoolean("generate_uniform_bindings"),
        fileOps);

    // Validate reference shaders.
    if (!ns.getBoolean("disable_validator")) {
      if (!fileOps.areShadersValid(preparedReferenceShaderJob, false)) {
        throw new RuntimeException("One or more of the prepared shaders of shader job "
            + preparedReferenceShaderJob.getAbsolutePath() + " is not valid.");
      }
    }

    if (primitivesFile(referenceShaderJob).isFile()) {
      /*
      shutil.copyfile(primitives_file(args.reference_prefix),
          args.output_folder + "reference.primitives")
    primitives_data = json.load(open(primitives_file(args.reference_prefix)))
    if "texture" in primitives_data:
    texture_filename = primitives_data["texture"]
    shutil.copyfile(texture_filename, args.output_folder + texture_filename)
          */
    }

    int generatedVariants = 0;
    int triedVariants = 0;
    int chunkCount = 0;

    final int numVariants = ns.getInt("num_variants");
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

      // Think about whether we need a timeout
      triedVariants++;
      try {
        Generate.generateVariant(fileOps, preparedReferenceShaderJob, variantShaderJobFile,
            Generate.getGeneratorArguments(ns), ns.getBoolean("write_probabilities"));
      } catch (Exception exception) {
        if (verbose) {
          LOGGER.error("Failed generating variant:");
          LOGGER.error(exception.getMessage());
          exception.printStackTrace();
        }
        if (ns.getBoolean("stop_on_fail")) {
          final String message = "Failed generating a variant, stopping.";
          LOGGER.info(message);
          throw new RuntimeException(message);
        }
        continue;
      }

      // Check the shader is valid
      if (skipDueToInvalidShader(variantShaderJobFile, verbose)) {
        continue;
      }

      // Check code size
      if (generatedShadersTooLarge(fileOps,
          preparedReferenceShaderJob,
          variantShaderJobFile,
          ns.get("max_factor") == null ? Optional.empty() : Optional.of(ns.getFloat("max_factor")),
          ns.get("max_bytes") == null ? Optional.empty() : Optional.of(ns.getInt("max_bytes")),
          verbose)) {
        // A generated shader is too large - discard it (but don't log it as bad)
        continue;
      }

      if (primitivesFile(preparedReferenceShaderJob).isFile()) {
        //shutil.copyfile("reference.primitives", primitives_file(variant_file_prefix))
      }

      generatedVariants += 1;
    }

    // Final steps
    /*
# Initialise json log file with version information
        log_json = {
        "git_hash": get_git_revision_hash(),
        "glsl_version": args.glsl_version,
        "webgl": args.webgl,
        "seed": args.seed,
        "reference_basename": os.path.basename(args.reference_prefix)
            }
            */
    /*
    # Output json log
        dict = {}
    dict['dict'] = log_json
    log_json_file = open("infolog.json", 'w')
    log_json_file.write(json.dumps(dict, sort_keys=True, indent=4))
    log_json_file.close()
    */

    LOGGER.info("Generation complete -- generated " + generatedVariants + " variants in "
            + triedVariants + " tries.");
  }

  private static File primitivesFile(File shaderJob) {
    throw new RuntimeException("TODO");
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

  private static boolean skipDueToInvalidShader(File variantShaderJobFile,
                                                boolean disableValidator) {
    if (disableValidator) {
      // Validation has been disabled, so don't do any skipping.
      return false;
    }

    throw new RuntimeException("TODO: finish this; need to make sure we invoke shader translator");

    /*
    variant_file_frag = variant_file_prefix + ".frag"
    variant_file_vert = variant_file_prefix + ".vert"
    variant_file_json = variant_file_prefix + ".json"
    variant_file_probabilities = variant_file_prefix + ".prob"

    for ext in [ ".frag", ".vert" ]:
        variant_shader_file = variant_file_prefix + ext
        if not os.path.isfile(variant_shader_file):
            continue
        if shader_is_valid(variant_shader_file):
            continue
        if not args.keep_bad_variants:
            remove_if_exists(variant_file_frag)
            remove_if_exists(variant_file_vert)
            os.remove(variant_file_json)
            os.remove(variant_file_probabilities)
            return True
        else:
            move_if_exists(variant_file_frag, "bad_" + os.path.basename(variant_file_frag))
            move_if_exists(variant_file_vert, "bad_" + os.path.basename(variant_file_vert))
            shutil.move(variant_file_json, "bad_" + os.path.basename(variant_file_json))
            shutil.move(variant_file_probabilities, "bad_" +
            os.path.basename(variant_file_probabilities))
        if args.stop_on_fail:
            if args.verbose:
                print("Generated an invalid variant, stopping.")
            exit(1)
        return False
     */
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



/*
sys.path.append(os.path.split(os.path.abspath(__file__))[0] + os.path.sep + "..")
from cmd_helpers import validate_frag
from cmd_helpers import execute

this_path = os.path.split(os.path.abspath(__file__))[0] + os.path.sep
max_int = pow(2, 16)


### Helper functions

def abs_path_with_extension(prefix, extension):
    return os.path.abspath(prefix + extension)

def uniforms_file(prefix):
    return abs_path_with_extension(prefix, ".json")

def primitives_file(prefix):
    return abs_path_with_extension(prefix, ".primitives")

def license_file(prefix):
    return abs_path_with_extension(prefix, ".license")

def uniforms_present(prefix):
    return os.path.isfile(uniforms_file(prefix))

def kill_generator(generator_proc):
    if args.verbose:
        print("Timeout")
    generator_proc.kill()



def get_git_revision_hash():
    git_hash = open(args.hash_file, 'r')
    ret_val = git_hash.read()
    git_hash.close()
    return ret_val


def shader_is_valid(shader_file):
    if args.verbose:
        print("Validating...")
    validator_results = validate_frag(shader_file, args.validator_path, args.verbose)
    if validator_results["returncode"] == 0 and args.webgl:
        validator_results = execute([args.translator_path, "-s=w", shader_file], args.verbose)
    elif validator_results["returncode"] == 0 and args.glsl_version == "100":
        validator_results = execute([args.translator_path, shader_file], args.verbose)
    if validator_results["returncode"] != 0:
        if args.verbose:
            print("Failed validating shader:")
            print(validator_results["stdout"].decode("utf-8"))
            print(validator_results["stderr"].decode("utf-8"))
        return False
    return True

def generated_shaders_too_large(args, variant_file_prefix):
    for ext in [ ".frag", ".vert" ]:
        variant_shader_file = variant_file_prefix + ext
        if not os.path.isfile(variant_shader_file):
            continue
        num_bytes_variant = os.path.getsize(variant_shader_file)
        num_bytes_reference = os.path.getsize(args.reference_prefix + ext)

        if args.max_factor is not None and float(num_bytes_variant) > args.max_factor *
        float(num_bytes_reference):
            if args.verbose:
                print("Discarding " + ext + " shader of size " + str(num_bytes_variant) + " bytes;
                more than " + str(args.max_factor) + " times larger than reference of size " +
                str(num_bytes_reference))
            return True

        if args.max_bytes is not None and num_bytes_variant > args.max_bytes:
            if args.verbose:
                print("Discarding " + ext + " shader of size " + str(num_bytes_variant) + " bytes;
                exceeds limit of " + str(args.max_bytes) + " bytes")
            return True

    return False

def remove_if_exists(filename):
    if os.path.isfile(filename):
        os.remove(filename)

def move_if_exists(src, dst):
    print(src)
    print(dst)
    if os.path.isfile(src):
        shutil.move(src, dst)

def skip_due_to_invalid_shader(args, variant_file_prefix):
    variant_file_frag = variant_file_prefix + ".frag"
    variant_file_vert = variant_file_prefix + ".vert"
    variant_file_json = variant_file_prefix + ".json"
    variant_file_probabilities = variant_file_prefix + ".prob"

    if args.disable_validator:
        return False
    for ext in [ ".frag", ".vert" ]:
        variant_shader_file = variant_file_prefix + ext
        if not os.path.isfile(variant_shader_file):
            continue
        if shader_is_valid(variant_shader_file):
            continue
        if not args.keep_bad_variants:
            remove_if_exists(variant_file_frag)
            remove_if_exists(variant_file_vert)
            os.remove(variant_file_json)
            os.remove(variant_file_probabilities)
            return True
        else:
            move_if_exists(variant_file_frag, "bad_" + os.path.basename(variant_file_frag))
            move_if_exists(variant_file_vert, "bad_" + os.path.basename(variant_file_vert))
            shutil.move(variant_file_json, "bad_" + os.path.basename(variant_file_json))
            shutil.move(variant_file_probabilities, "bad_" +
            os.path.basename(variant_file_probabilities))
        if args.stop_on_fail:
            if args.verbose:
                print("Generated an invalid variant, stopping.")
            exit(1)
        return False


### Initial setup
HERE


 */

}
