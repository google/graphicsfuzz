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
import json
import os
import sys
from typing import List

import inspect_compute_results


def exact_match(reference_result: str, variant_result: str) -> bool:
    result, _ = inspect_compute_results.exactdiff_ssbos(reference_result, variant_result)
    return result


def fuzzy_match(reference_result: str, variant_result: str, args: argparse.Namespace) -> bool:
    if args.rel_tol:
        rel_tol = float(args.rel_tol)
    else:
        rel_tol = float(inspect_compute_results.DEFAULT_REL_TOL)
    if args.abs_tol:
        abs_tol = float(args.abs_tol)
    else:
        abs_tol = float(inspect_compute_results.DEFAULT_ABS_TOL)
    result, _ = inspect_compute_results.fuzzydiff_ssbos(reference_result, variant_result,
                                                        rel_tol=rel_tol, abs_tol=abs_tol)
    return result


def main_helper(args: List[str]) -> None:
    description = (
        'Report results for a compute shader family.')

    parser = argparse.ArgumentParser(description=description)

    # Required arguments
    parser.add_argument(
        'results_directory',
        help='A directory containing results for a compute shader family')

    parser.add_argument(
        '--rel_tol',
        help=(
            'Relative tolerance parameter for fuzzy diffing, default: '
            + inspect_compute_results.DEFAULT_REL_TOL))

    parser.add_argument(
        '--abs_tol',
        help=(
            'Absolute tolerance parameter for fuzzy diffing, default: '
            + inspect_compute_results.DEFAULT_ABS_TOL))

    args = parser.parse_args(args)

    results_directory = args.results_directory
    if not os.path.isdir(results_directory):
        raise FileNotFoundError('Specified results directory "' + results_directory + '" not found')

    reference_result = results_directory + os.sep + 'reference.info.json'
    if not os.path.isfile(reference_result):
        raise FileNotFoundError(
            'No results found for reference shader; expected file "reference.info.json" missing')

    with open(reference_result, 'r') as reference_result_file:
        reference_json = json.load(reference_result_file)

    reference_status = reference_json['status']
    sys.stdout.write(reference_result + ': ' + reference_status + '\n')
    if reference_status != 'SUCCESS':
        sys.stdout.write('Cannot compare variant results as reference failed')
        return

    for variant_result in glob.glob(results_directory + os.sep + 'variant*.info.json'):
        with open(variant_result, 'r') as variant_result_file:
            variant_json = json.load(variant_result_file)
        variant_status = variant_json['status']
        sys.stdout.write(variant_result + ': ' + variant_status)
        if variant_status == 'SUCCESS':
            if exact_match(reference_result, variant_result):
                sys.stdout.write(', EXACT_MATCH')
            elif fuzzy_match(reference_result, variant_result, args):
                sys.stdout.write(', FUZZY_MATCH')
            else:
                sys.stdout.write(', DIFFERENT')
        sys.stdout.write('\n')


if __name__ == '__main__':
    main_helper(sys.argv[1:])
