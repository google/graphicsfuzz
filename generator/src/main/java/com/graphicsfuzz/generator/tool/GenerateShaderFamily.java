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

import java.io.File;
import java.util.Random;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
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

    parser.addArgument("--num-variants")
        .help("Number of variants to produce.")
        .type(Integer.class)
        .setDefault(10);

    parser.addArgument("--seed")
        .help("Seed to initialize random number generator with.")
        .type(Integer.class)
        .setDefault(new Random().nextInt());

    parser.addArgument("--timeout")
        .help("Time in seconds after which execution of generation is terminated.")
        .type(Integer.class)
        .setDefault(30);

    parser.addArgument("--hash_file")
        .help("Path to file containing git hash.")
        .type(File.class)
        .setDefault("TODO");

    parser.addArgument("--disable_validator")
        .help("Disable calling glslangValidator for generated variants.")
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

    parser.addArgument("--small")
        .help("Restrict generation to small shader families.")
        .action(Arguments.storeTrue());

    parser.addArgument("--webgl")
        .help("Restrict transformations to be WebGL-compatible.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max-bytes")
        .help("Maximum allowed size, in bytes, for variant shader (default: no limit).")
        .type(Integer.class);

    parser.addArgument("--max-factor")
        .help("Maximum blowup allowed, compared with size of reference shader (default: no limit).")
        .type(Float.class);

    parser.addArgument("--avoid-long-loops")
        .help("Avoid long-running loops during live code injection.")
        .action(Arguments.storeTrue());

    parser.addArgument("--aggressively-complicate-control-flow")
        .help("Generate very complex control flow.")
        .action(Arguments.storeTrue());

    parser.addArgument("--multi-pass")
        .help("Apply shader transformations multiple times.")
        .action(Arguments.storeTrue());

    parser.addArgument("--replace-float-literals")
        .help("Replace floating-point literals with uniforms.")
        .action(Arguments.storeTrue());

    parser.addArgument("--require-license")
        .help("Require a license file to be provided alongside the reference and pass details "
            + "through to generated shaders.")
        .action(Arguments.storeTrue());

    parser.addArgument("--generate-uniform-bindings")
        .help("Put all uniforms in uniform blocks and generate associated bindings.  Necessary for "
            + "Vulkan compatibility.")
        .action(Arguments.storeTrue());

    parser.addArgument("--max-uniforms")
        .help("Ensure that no more than the given number of uniforms are included in generated "
            + "shaders.  Necessary for Vulkan compatibility.")
        .type(Integer.class);

    return parser.parseArgs(args);

  }

  public static void mainHelper(String[] args) throws ArgumentParserException {

    Namespace ns = parse(args);

    final int seed = ns.getInt("seed");
    final boolean verbose = ns.getBoolean("verbose");
    if (verbose) {
      LOGGER.info("Using seed " + seed);
    }

    final File outputDir = ns.get("output_dir") == null ? new File(".") : ns.get("output_dir");
    final File donorsDir = ns.get("donors");
    final File referenceShaderJob = ns.get("reference_shader_job");

    if (verbose) {
      LOGGER.info("Using donor foler " + donorsDir.getAbsolutePath());
    }

    if (outputDir.isDirectory()) {
      LOGGER.info("Overwriting previous output directory (" + outputDir.getAbsolutePath());
    }

    /*
    shutil.rmtree(args.output_folder)
    os.mkdir(args.output_folder)

    if not uniforms_present(args.reference_prefix):
    print("No uniforms file present for reference '" + args.reference_prefix + "'")
    exit(1)

# Prepare reference shaders
    prepare_reference_shaders(args.reference_prefix, args.output_folder + "reference",
    args.glsl_version)

# Validate reference shaders
    if not args.disable_validator:
    if not shader_is_valid(args.output_folder + "reference.frag"):
    print("The reference fragment shader is not valid, stopping.")
    exit(1)
    vertex_shader = args.output_folder + "reference.vert"
    if os.path.isfile(vertex_shader) and not shader_is_valid(vertex_shader):
    print("The reference vertex shader is not valid, stopping.")
    exit(1)

# Copy primitives for reference shaders, if they exist
    if os.path.isfile(primitives_file(args.reference_prefix)):
    shutil.copyfile(primitives_file(args.reference_prefix),
        args.output_folder + "reference.primitives")

# Copy texture if referenced by primitives
    if os.path.isfile(primitives_file(args.reference_prefix)):
    primitives_data = json.load(open(primitives_file(args.reference_prefix)))
    if "texture" in primitives_data:
    texture_filename = primitives_data["texture"]
    shutil.copyfile(texture_filename, args.output_folder + texture_filename)

    os.chdir(args.output_folder)

    donor_list = glob.glob(args.donors + os.path.sep + "*.frag")
    if not donor_list:
    print("Given donor folder contains no .frag files!")
    exit(1)

# Initialise json log file with version information
        log_json = {
        "git_hash": get_git_revision_hash(),
        "glsl_version": args.glsl_version,
        "webgl": args.webgl,
        "seed": args.seed,
        "reference_basename": os.path.basename(args.reference_prefix)
            }

    gen_variants = 0
    tried_variants = 0
    chunk_count = 0

# Main variant generation loop
    while gen_variants < args.num_variants:

    if args.verbose:
    print("Trying variant %d (produced %d of %d)..." % (tried_variants, gen_variants,
    args.num_variants))
    # Generation
        inner_seed = random.randint(0, max_int)
    if args.verbose:
    print("Generating variant with inner seed %d..." % inner_seed)
    variant_file_prefix = "variant_" + "{0:0=3d}".format(gen_variants)
    generator_results = generate_variant(args.reference_prefix, inner_seed, variant_file_prefix)
    tried_variants += 1
    if generator_results["returncode"] != 0:
    if args.verbose:
    print("Failed generating variant:")
    print(generator_results["stderr"].decode("utf-8"))
    if args.stop_on_fail:
    if args.verbose:
    print("Failed generating a variant, stopping.")
    exit(1)
    continue

    # Check code size
    if generated_shaders_too_large(args, variant_file_prefix):
        # A generated shader is too large - discard it (but don't log it as bad)
    continue

    # Validation
    if skip_due_to_invalid_shader(args, variant_file_prefix):
    continue

    if os.path.isfile("reference.primitives"):
    shutil.copyfile("reference.primitives", primitives_file(variant_file_prefix))

    gen_variants += 1

### Final steps
# Output json log
        dict = {}
    dict['dict'] = log_json
    log_json_file = open("infolog.json", 'w')
    log_json_file.write(json.dumps(dict, sort_keys=True, indent=4))
    log_json_file.close()

    print("Generation complete -- generated %d variants in %d tries." % (gen_variants,
    tried_variants))

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

def generate_variant(reference_prefix, inner_seed, output_prefix):
    cmd = ["java", "-ea",
           "com.graphicsfuzz.generator.tool.Generate",
           reference_prefix + ".json",
           args.donors,
           args.glsl_version,
           output_prefix + ".json",
           "--seed",
           str(inner_seed) ]
    if args.webgl:
        cmd += [ "--webgl" ]
    if args.small:
        cmd += [ "--small" ]
    if args.avoid_long_loops:
        cmd += [ "--avoid_long_loops" ]
    if args.replace_float_literals:
        cmd += [ "--replace_float_literals" ]
    if args.aggressively_complicate_control_flow:
        cmd += [ "--aggressively_complicate_control_flow" ]
    if args.multi_pass:
        cmd += [ "--multi_pass" ]
    if args.generate_uniform_bindings:
        cmd += [ "--generate_uniform_bindings" ]
    if args.max_uniforms is not None:
        cmd += [ "--max_uniforms", str(args.max_uniforms) ]
    if args.verbose:
        print("Transform command: %s" % (" ".join(cmd)))
    generator_proc = subprocess.Popen(cmd,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    kill_timer = threading.Timer(args.timeout, lambda: kill_generator(generator_proc))
    try:
        kill_timer.start()
        generator_stdout, generator_stderr = generator_proc.communicate()
        assert (generator_proc.returncode is not None)
    finally:
        kill_timer.cancel()
    return {"returncode": generator_proc.returncode,
            "stdout": generator_stdout,
            "stderr": generator_stderr,
            "cmd": " ".join(cmd)}

def prepare_reference_shaders(reference_prefix, output_file_prefix, glsl_version):
    cmd = ["java",
           "-ea",
           "com.graphicsfuzz.generator.tool.PrepareReference",
           reference_prefix + ".json",
           output_file_prefix + ".json",
           glsl_version ]
    if args.replace_float_literals:
        cmd += [ "--replace_float_literals" ]
    if args.webgl:
        cmd += [ "--webgl" ]
    if args.generate_uniform_bindings:
        cmd += [ "--generate_uniform_bindings" ]
    if args.max_uniforms is not None:
        cmd += [ "--max_uniforms", str(args.max_uniforms - 1) ] # We subtract 1 because we need to
        be able to add injectionSwitch
    if args.verbose:
        print("Reference preparation command: %s" % (" ".join(cmd)))
    prepare_reference_proc = subprocess.Popen(cmd,
                                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    prepare_reference_stdout, prepare_reference_stderr = prepare_reference_proc.communicate()
    if prepare_reference_proc.returncode != 0:
        print("Reference preparation resulted in errors: " + prepare_reference_stderr)
        sys.exit(1)

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

}
