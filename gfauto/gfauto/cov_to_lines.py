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

"""Outputs number of lines covered from .cov files."""

import argparse
import pickle
import sys
from pathlib import Path
from typing import List

from gfauto import cov_util, util
from gfauto.gflogging import log


def main() -> None:

    parser = argparse.ArgumentParser(
        description="Outputs number of lines covered from .cov files.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument(
        "coverage_files",
        metavar="coverage_files",
        type=str,
        nargs="*",
        help="The .cov files to process, one after the other.",
    )

    parser.add_argument(
        "--out", type=str, help="Output results text file.", default="out.txt",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    input_files: List[str] = parsed_args.coverage_files
    output_file: str = parsed_args.out

    with util.file_open_text(Path(output_file), "w") as out:

        for coverage_file in input_files:
            with open(coverage_file, mode="rb") as f:
                all_line_counts: cov_util.LineCounts = pickle.load(f)

            total_num_lines = 0
            total_num_covered_lines = 0

            # |all_line_counts| maps from source file to another map. We just need the map.
            for line_counts in all_line_counts.values():
                # |line_counts| maps from line number to execution count. We just need the execution count.
                for execution_count in line_counts.values():
                    total_num_lines += 1
                    if execution_count > 0:
                        total_num_covered_lines += 1

            log(f"{total_num_covered_lines}, {total_num_lines}")
            out.write(f"{total_num_covered_lines}, {total_num_lines}\n")


if __name__ == "__main__":
    main()
