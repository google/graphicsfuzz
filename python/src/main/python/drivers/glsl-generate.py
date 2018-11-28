#!/usr/bin/env python

# Copyright 2018 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import argparse
import glob
import os
import random
import subprocess
import sys

### Argument parser
parser = argparse.ArgumentParser(description="Generate a set of shader families")

# Required arguments
parser.add_argument("donors", type=str, action="store",
                    help="Path to directory of donor shaders.")
parser.add_argument("references", type=str, action="store",
                    help="Path to directory of reference shaders.")
parser.add_argument("num_variants", type=int, action="store",
                    help="Number of variants to be produced for each shader in the donor set.")
parser.add_argument("glsl_version", type=str, action="store",
                    help="Version of GLSL to be used.")
parser.add_argument("prefix", type=str, action="store",
                    help="String with which to prefix shader family name.")
parser.add_argument("output_folder", type=str, action="store",
                    help="Output directory for shader families.")

# Optional arguments
parser.add_argument("--webgl", action="store_true",
                    help="Restrict transformations to be WebGL-compatible.")
parser.add_argument("--keep_bad_variants", action="store_true",
                    help="Do not remove invalid variants upon generation.")
parser.add_argument("--stop_on_fail", action="store_true",
                    help="Quit if an invalid variant is generated.")
parser.add_argument("--verbose", action="store_true",
                    help="Emit detailed information regarding the progress of the generation.")
parser.add_argument("--small", action="store_true",
                    help="Try to generate smaller variants.")
parser.add_argument("--avoid_long_loops", action="store_true",
                    help="Avoid long-running loops during live code injection.")
parser.add_argument("--max_bytes", type=int, action="store",
                    help="Restrict maximum size of generated variants.")
parser.add_argument("--max_factor", type=float, action="store",
                    help="Maximum blowup allowed, compared with size of reference shader (default: no limit).")
parser.add_argument("--replace_float_literals", action="store_true",
                    help="Replace float literals with uniforms.")
parser.add_argument("--multi_pass", action="store_true",
                    help="Apply multiple transformation passes.")
parser.add_argument("--require_license", action="store_true",
                    help="Require that each shader has an accompanying license, and use this during generation.")
parser.add_argument("--generate_uniform_bindings", action="store_true",
                    help="Put all uniforms in uniform blocks and generate associated bindings.  Necessary for Vulkan compatibility.")
parser.add_argument("--max_uniforms", type=int, action="store",
                    help="Ensure that no more than the given number of uniforms are included in generated shaders.  Necessary for Vulkan compatibility.")

args = parser.parse_args()

generate_shader_family = os.sep.join([os.path.dirname(os.path.abspath(__file__)), "generate-shader-family.py"])

references = glob.glob(args.references + os.sep + "*.frag")

if not references:
    print("Warning: no references found in " + args.references)
else:
    print("About to generate "
          + str(len(references)) + " shader famil" + ("y" if len(references) == 0 else "ies")
          + ", each with " + str(args.num_variants) + " variant" + ("" if args.num_variants == 1 else "s") + ".")

reference_count = 0
for reference in glob.glob(args.references + os.sep + "*.frag"):
    print("Generating family " + str(reference_count) + " from reference " + reference)
    reference_count += 1
    cmd = [
        "python",
        generate_shader_family,
        os.path.splitext(reference)[0],
        args.donors,
        args.glsl_version,
        os.path.join(
            args.output_folder,
            args.prefix + "_" + os.path.splitext(os.path.basename(reference))[0]
        ),
        "--num_variants",
        str(args.num_variants),
        "--seed",
        str(random.randint(0, pow(2, 16)))
    ]
    if args.webgl:
        cmd.append("--webgl")
    if args.verbose:
        cmd.append("--verbose")
    if args.keep_bad_variants:
        cmd.append("--keep_bad_variants")
    if args.stop_on_fail:
        cmd.append("--stop_on_fail")
    if args.small:
        cmd.append("--small")
    if args.avoid_long_loops:
        cmd.append("--avoid_long_loops")
    if args.max_bytes is not None:
        cmd.append("--max_bytes")
        cmd.append(str(args.max_bytes))
    if args.max_factor is not None:
        cmd.append("--max_factor")
        cmd.append(str(args.max_factor))
    if args.replace_float_literals:
        cmd.append("--replace_float_literals")
    if args.multi_pass:
        cmd.append("--multi_pass")
    if args.require_license:
        cmd.append("--require_license")
    if args.generate_uniform_bindings:
        cmd.append("--generate_uniform_bindings")
    if args.max_uniforms is not None:
        cmd.append("--max_uniforms")
        cmd.append(str(args.max_uniforms))

    if args.verbose:
        print("Generating a shader family: " + " ".join(cmd))

    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    proc_stdout, proc_stderr = proc.communicate()
    if proc.returncode != 0:
        print("Error code " + str(proc.returncode) + " returned during generation of shader family for " + reference)
        if args.stop_on_fail:
            print("Stopping.")
            exit(1)
    if args.verbose:
        print("")
        print(" ".join(cmd))
        print(proc_stdout)
        print(proc_stderr)
        print("")
