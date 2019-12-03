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

"""Takes a .cov file and outputs annotated source files.

Unlike most code in gfauto, we use str instead of pathlib.Path because the increased speed is probably worthwhile.
"""

import argparse
import pickle
import sys

from gfauto import cov_util


def main() -> None:

    parser = argparse.ArgumentParser(
        description="Takes a .cov file and outputs annotated source files.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument(
        "--coverage_out",
        type=str,
        help="The output directory for source files annotated with line coverage.",
        default="",
    )

    parser.add_argument(
        "--zero_coverage_out",
        type=str,
        help="The output directory for source files annotated with line coverage, assuming zero coverage.",
        default="",
    )

    parser.add_argument("--cov", type=str, help="The .cov file.", default="output.cov")

    parser.add_argument(
        "build_dir",
        type=str,
        help="The build directory where the compiler was invoked.",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    coverage_out: str = parsed_args.coverage_out
    zero_coverage_out: str = parsed_args.zero_coverage_out
    coverage_file: str = parsed_args.cov
    build_dir: str = parsed_args.build_dir

    with open(coverage_file, mode="rb") as f:
        line_counts: cov_util.LineCounts = pickle.load(f)

    if coverage_out:
        cov_util.output_source_files(build_dir, coverage_out, line_counts)

    if zero_coverage_out:
        cov_util.output_source_files(
            build_dir, zero_coverage_out, line_counts, force_zero_coverage=True
        )


if __name__ == "__main__":
    main()
