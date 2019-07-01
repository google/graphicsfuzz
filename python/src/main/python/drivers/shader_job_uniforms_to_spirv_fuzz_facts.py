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
import struct
import sys
from typing import Any, Dict, List

import runspv


# Turns a GraphicsFuzz .json file into a spirv-fuzz .facts file, with one fact per word of uniform
# data.
def main_helper(args) -> None:
    parser = argparse.ArgumentParser()

    # Required arguments
    parser.add_argument(
        'shader_job',
        help='Shader job (.json) file.')

    parser.add_argument(
        'output_file',
        help='Output file for facts.')

    args = parser.parse_args(args)

    # Generate uniform facts from .json file
    fact_list = []  # type: List[dict]

    # Turn the information about uniform values into facts for spirv-fuzz.
    with runspv.open_helper(args.shader_job, 'r') as f:
        j = json.load(f)  # type: Dict[str, Any]
    for name, entry in j.items():

        # Skip compute shader data
        if name == "$compute":
            continue

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

    with runspv.open_helper(args.output_file, 'w') as f:
        f.write(json.dumps(dict(fact=fact_list), indent=1, sort_keys=True))


if __name__ == '__main__':
    main_helper(sys.argv[1:])
