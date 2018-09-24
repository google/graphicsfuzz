#!/usr/bin/env python3

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
import os
import sys
import json

################################################################################

def rewrite_uniform_decl(line, bindingIdx):
    assert(line.startswith('uniform '))
    line = line.replace(
            'uniform ',
            'layout(set = 0, binding = {}) uniform buf{} {{'.format(bindingIdx, bindingIdx))
    line = line.rstrip() + ' };\n'
    return line

################################################################################

def extract_uniform_name(line):
    assert(line.startswith('uniform '))
    lastword = line.rstrip().split()[-1]
    assert(lastword.endswith(';'))
    lastword = lastword.replace(';', '')
    return lastword

################################################################################

def vulkanize(variantFrag, output):
    assert(variantFrag.endswith('.frag'))
    assert(os.path.isfile(variantFrag))

    variantJSON = variantFrag.replace('.frag', '.json')
    assert(os.path.isfile(variantJSON))

    uniformOrder = []
    bindingIdx = 0

    outFragFile = output + '.frag'
    outJSONFile = output + '.json'

    with open(variantFrag, 'r') as f:
        with open(outFragFile, 'w') as outFrag:
            skipMainOpenBracket = False
            for l in f.readlines():
                if '#version 300 es' in l:
                    outFrag.write('#version 310 es\n')
                elif l.startswith('uniform '):
                    uniformOrder += [extract_uniform_name(l)]
                    outFrag.write(rewrite_uniform_decl(l, bindingIdx))
                    bindingIdx += 1
                else:
                    # Workaround to counter the Y axis reversal
                    # This is not that easy because resolution uniform may not be declared
                    # l = l.replace('gl_FragCoord.y', '(resolution.y - gl_FragCoord.y)')
                    outFrag.write(l)

    with open(variantJSON, 'r') as f:
        uniformJSON = json.load(f)
        uniformJSON['uniformOrder'] = uniformOrder

    with open(outJSONFile, 'w') as outJSON:
        outJSON.write(json.dumps(uniformJSON, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('variantFrag', type=str, help='Fragment shader to vulkanize')
    parser.add_argument('output', type=str, help='Output name template, to eventually get: output.frag and output.json')

    args = parser.parse_args()

    vulkanize(args.variantFrag, args.output)
