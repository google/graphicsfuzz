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
import math
import os
import sys
from typing import Callable, List, Optional, Tuple


DEFAULT_REL_TOL = '1e-9'
DEFAULT_ABS_TOL = '1e-20'


def get_ssbo(result_json_filename: str) -> List:
    with open(result_json_filename, 'r', encoding='utf-8', errors='ignore') as f:
        parsed = json.load(f)
    if not parsed or 'outputs' not in parsed or 'ssbo' not in parsed['outputs']:
        raise ValueError('No SSBO data found')
    return parsed['outputs']['ssbo']


def show_ssbo(result_json_filename: str) -> None:
    try:
        print(json.dumps(get_ssbo(result_json_filename)))
    except ValueError as value_error:
        print(str(value_error))


def get_ssbo_pair(result_json_filename_1: str, result_json_filename_2: str) ->\
        Optional[Tuple[List, List]]:
    try:
        ssbo_1 = get_ssbo(result_json_filename_1)
    except ValueError:
        sys.stderr.write('First input file did not contain valid SSBO data')
        return None
    try:
        ssbo_2 = get_ssbo(result_json_filename_2)
    except ValueError:
        sys.stderr.write('Second input file did not contain valid SSBO data')
        return None
    return ssbo_1, ssbo_2


def abstract_diff_ssbos(result_json_filename_1: str,
                        result_json_filename_2: str,
                        comparator: Callable[[float, float], bool]) -> int:
    ssbo_pair = get_ssbo_pair(result_json_filename_1, result_json_filename_2)
    if not ssbo_pair:
        return 1
    ssbo_1 = ssbo_pair[0]
    ssbo_2 = ssbo_pair[1]
    if len(ssbo_1) != len(ssbo_2):
        sys.stderr.write('SSBOs have different numbers of fields: ' + str(len(ssbo_1)) + ' vs. '
                         + str(len(ssbo_2)) + '\n')
        return 1
    for i in range(0, len(ssbo_1)):
        if len(ssbo_1[i]) != len(ssbo_2[i]):
            sys.stderr.write('Data for field ' + str(i) + ' has different lengths: '
                             + str(len(ssbo_1[i])) + ' vs. ' + str(len(ssbo_2[i])) + '\n')
            return 1
        for j in range(0, len(ssbo_1[i])):
            if not comparator(float(ssbo_1[i][j]), float(ssbo_2[i][j])):
                sys.stderr.write('Mismatch at field ' + str(i) + ' element ' + str(j) + ': '
                                 + str(ssbo_1[i][j]) + ' vs. ' + str(ssbo_2[i][j]) + '\n')
                return 1
    return 0


def exactdiff_ssbos(result_json_filename_1: str, result_json_filename_2: str) -> int:
    return abstract_diff_ssbos(result_json_filename_1, result_json_filename_2, lambda x, y: x == y)


def fuzzydiff_ssbos(result_json_filename_1: str,
                    result_json_filename_2: str,
                    abs_tol: float,
                    rel_tol: float) -> int:
    return abstract_diff_ssbos(result_json_filename_1, result_json_filename_2,
                               lambda x, y: math.isclose(x, y, rel_tol=rel_tol, abs_tol=abs_tol))


def check_input_files_exist(filenames: List[str]) -> None:
    for filename in filenames:
        if not os.path.isfile(filename):
            raise FileNotFoundError('Input file "' + filename + '" not found')


def main_helper(args: List[str]) -> int:
    description = (
        'Inspect and compare compute shader outputs.')

    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument('command', help=(
        '"show": show contents of the SSBO for a given JSON output file; "exactdiff": show '
        'differences between SSBOs for two given JSON files, returning 0 if and only if they are '
        'identical; "fuzzydiff": show differences between SSBOs for two given JSON files, '
        'tolerating small differences between values, returning 0 if and only if they are '
        'identical modulo this tolerance'))

    parser.add_argument(
        'inputs',
        help='One or two JSON result files',
        nargs='+')

    parser.add_argument(
        '--rel_tol',
        help=(
            'Relative tolerance parameter for "fuzzydiff", default: ' + DEFAULT_REL_TOL
            + '.  Ignored unless "fuzzydiff" is used.'))

    parser.add_argument(
        '--abs_tol',
        help=(
            'Absolute tolerance parameter for "fuzzydiff", default: ' + DEFAULT_ABS_TOL
            + '.  Ignored unless "fuzzydiff" is used.'))

    args = parser.parse_args(args)

    try:
        if args.abs_tol:
            abs_tol = float(args.abs_tol)
        else:
            abs_tol = float(DEFAULT_ABS_TOL)
        if abs_tol < 0.0:
            raise ValueError
    except ValueError:
        raise ValueError('Non-negative floating-point value required for --abs_tol argument')

    try:
        if args.rel_tol:
            rel_tol = float(args.rel_tol)
        else:
            rel_tol = float(DEFAULT_REL_TOL)
        if rel_tol <= 0.0:
            raise ValueError
    except ValueError:
        raise ValueError('Positive floating-point value required for --rel_tol argument')

    if args.command == 'show':
        if len(args.inputs) != 1:
            raise ValueError(
                'Command "show" requires exactly 1 input; ' + str(len(args.inputs)) + ' provided')
        check_input_files_exist([args.inputs[0]])
        show_ssbo(args.inputs[0])
        return 0

    if args.command == 'exactdiff':
        if len(args.inputs) != 2:
            raise ValueError('Command "exactdiff" requires exactly 2 inputs; ' + str(
                len(args.inputs)) + ' provided')
        check_input_files_exist([args.inputs[0], args.inputs[1]])
        return exactdiff_ssbos(args.inputs[0], args.inputs[1])

    if args.command == 'fuzzydiff':
        if len(args.inputs) != 2:
            raise ValueError('Command "fuzzydiff" requires exactly 2 inputs; ' + str(
                len(args.inputs)) + ' provided')
        check_input_files_exist([args.inputs[0], args.inputs[1]])
        return fuzzydiff_ssbos(args.inputs[0], args.inputs[1], abs_tol, rel_tol)

    raise ValueError('Unknown command "' + args.command + '"')


if __name__ == '__main__':
    sys.exit(main_helper(sys.argv[1:]))
