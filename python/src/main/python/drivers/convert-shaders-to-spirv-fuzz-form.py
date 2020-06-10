#!/usr/bin/env python3

# Copyright 2019 The GraphicsFuzz Project Authors
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
import shutil
import sys

import runspv
import graphicsfuzz_tool
import shader_job_uniforms_to_spirv_fuzz_facts


HERE = os.path.abspath(__file__)


def main_helper(args):
    description = (
        'Convert a directory of GLSL shader jobs to shader jobs suitable as inputs to spirv-fuzz.')

    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument('input_dir', help="A directory of GLSL shader jobs.")
    parser.add_argument('output_dir', help="The name of a directory that will be created and that "
                                           "will contain the resulting spirv-fuzz shader jobs.")

    args = parser.parse_args(args)

    if not os.path.isdir(args.input_dir):
        raise ValueError("Input directory " + "'" + args.input_dir + "' not found.")

    # Make the output directory if it does not yet exist.
    if not os.path.isdir(args.output_dir):
        os.makedirs(args.output_dir)

    for shader_job in glob.glob(os.path.join(args.input_dir, "*.json")):
        print(shader_job)
        shader_job_prefix = os.path.splitext(os.path.basename(shader_job))[0]
        json_in_output_dir = os.path.join(args.output_dir, shader_job_prefix + ".json")
        graphicsfuzz_tool.main_helper(["com.graphicsfuzz.generator.tool.PrepareReference",
                                       "--vulkan", "--max-uniforms", "10",
                                       shader_job, json_in_output_dir])
        shader_job_uniforms_to_spirv_fuzz_facts.main_helper([json_in_output_dir,
                                                             os.path.join(args.output_dir,
                                                                          shader_job_prefix
                                                                          + ".frag.facts")])
        runspv.convert_glsl_to_spv(os.path.join(args.output_dir, shader_job_prefix + ".frag"),
                                   os.path.join(args.output_dir, shader_job_prefix + ".frag.spv"))

        for opt_settings in ["-O", "-Os"]:
            shutil.copyfile(os.path.join(args.output_dir, shader_job_prefix + ".json"),
                            os.path.join(args.output_dir, shader_job_prefix + opt_settings
                                         + ".json"))
            shutil.copyfile(os.path.join(args.output_dir, shader_job_prefix + ".frag.facts"),
                            os.path.join(args.output_dir, shader_job_prefix + opt_settings
                                         + ".frag.facts"))
            runspv.run_spirv_opt(os.path.join(args.output_dir, shader_job_prefix + ".frag.spv"),
                                 [opt_settings],
                                 os.path.join(args.output_dir, shader_job_prefix + opt_settings
                                              + ".frag.spv"))


if __name__ == '__main__':
    main_helper(sys.argv[1:])
