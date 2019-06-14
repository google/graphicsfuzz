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
import json
import os
import random
import shutil
import struct
from typing import Optional, List, Union

import runspv


def main():
    parser = argparse.ArgumentParser()

    # Required arguments
    parser.add_argument(
        'shader_job',
        help='Shader job (.json) file.')

    parser.add_argument(
        'output_dir',
        help='Output directory in which to place temporary and result files')

    # Optional arguments
    parser.add_argument(
        '--seed',
        help='A seed to control random number generation.')

    parser.add_argument(
        '--spirvopt',
        help=runspv.SPIRV_OPT_OPTION_HELP + 'Options with which to invoke spirv-opt.')

    args = parser.parse_args()

    # Make the output directory if it does not yet exist.
    if not os.path.isdir(args.output_dir):
        os.makedirs(args.output_dir)

    fragment_shader = os.path.splitext(args.shader_job)[0] + ".frag"  # type: str
    if not os.path.isfile(fragment_shader):
        print('There is no fragment shader associated with "' + args.shader_job + '".')
        exit(1)

    # Seed the random number generator.  If no seed is provided then 'None' will be used, which
    # seeds the generator based on system time.
    random.seed(args.seed)

    # Turn the shader into SPIR-V, invoking spirv-opt along the way if spirv-opt arguments have been
    # given.
    spirvopt_args = None  # type: Union[Optional[List[str]], str]
    if args.spirvopt:
        spirvopt_args = args.spirvopt.split()
    spv_file = runspv.prepare_shader(args.output_dir, fragment_shader, spirvopt_args)  # type: str

    # For now we use fixed names for output files.
    shutil.copyfile(spv_file, args.output_dir + os.sep + "for_spirv_fuzz.spv")
    shutil.copyfile(args.shader_job, args.output_dir + os.sep + "for_spirv_fuzz.json")

    # Generate uniform facts from .json file
    fact_list = []  # type: list[dict]

    # Turn the information about uniform values into facts for spirv-fuzz.
    with runspv.open_helper(args.shader_job, 'r') as f:
        j = json.load(f)
    for name, entry in j.items():
        # Make a separate fact for every component value of each uniform.
        for i in range(0, len(entry["args"])):
            # Every uniform is in its own struct, so we index into the first element of that struct
            # using index 0.  If the uniform is a vector, we need to further index into the vector.
            if entry["func"] in ['glUniform1i', 'glUniform1f']:
                # The uniform is a scalar.
                assert i == 0
                index_list = [0]
            elif entry["func"] in ['glUniform2i', 'glUniform3i', 'glUniform4i', 'glUniform2f',
                                   'glUniform3f', 'glUniform4f']:
                # The uniform is a vector, so we have two levels of indexing.
                index_list = [0, i]
            else:
                # We can deal with other uniforms in due course.
                index_list = []
                print("Unsupported uniform function " + entry["func"])
                exit(1)

            # We need to pass the component value as an integer.  If it is a float, we need to
            # reinterpret its bits as an integer.
            int_representation = entry["args"][i]
            if isinstance(int_representation, float):
                int_representation = struct.unpack('<I', struct.pack('<f', entry["args"][i]))[0]

            uniform_buffer_element_descriptor = dict(descriptorSet=0, binding=entry["binding"],
                                                     index=index_list)

            fact_constant_uniform = dict(
                uniformBufferElementDescriptor=uniform_buffer_element_descriptor,
                constantWord=[int_representation])
            fact_list.append(dict(constantUniformFact=fact_constant_uniform))

    facts = { "fact": fact_list }
    with open(args.output_dir + os.sep + "for_spirv_fuzz.facts", "w") as f:
        f.write(json.dumps(facts, indent=1, sort_keys=True))


if __name__ == '__main__':
    main()
