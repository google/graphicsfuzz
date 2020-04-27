# -*- coding: utf-8 -*-

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

"""Outputs merged coverage data (merged.cov) given a list of .cov files.

Unlike most code in gfauto, we use str instead of pathlib.Path because the increased speed is probably worthwhile.
"""

import argparse
import pickle
import sys
from typing import List

from gfauto import cov_util


def main() -> None:

    parser = argparse.ArgumentParser(
        description="Outputs merged coverage data given a list of .cov files.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument(
        "--out", type=str, default="merged.cov", help="The output merged coverage file."
    )
    parser.add_argument(
        "coverage_files",
        metavar="coverage_files",
        type=str,
        nargs="*",
        help="The .cov files to merge.",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    output_file: str = parsed_args.out
    input_files: List[str] = parsed_args.coverage_files

    output_line_counts: cov_util.LineCounts = {}

    for input_file in input_files:
        with open(input_file, mode="rb") as f:
            input_line_counts: cov_util.LineCounts = pickle.load(f)
        add_line_counts_unsafe(input_line_counts, output_line_counts)

    with open(output_file, mode="wb") as f:
        pickle.dump(output_line_counts, f, protocol=pickle.HIGHEST_PROTOCOL)


def add_line_counts_unsafe(
    input_line_counts: cov_util.LineCounts, output_line_counts: cov_util.LineCounts
) -> None:
    """
    Merges input_line_counts into output_line_counts.

    WARNING: takes ownership of input_line_counts. I.e. input_line_counts should not be used after return.
    This is because Counter references from input_line_counts may be copied into output_line_counts (which may then get
    mutated further).
    """
    for file_path, line_counts in input_line_counts.items():
        if file_path not in output_line_counts:
            output_line_counts[file_path] = line_counts
        else:
            output_line_counts[file_path].update(line_counts)


if __name__ == "__main__":
    main()
