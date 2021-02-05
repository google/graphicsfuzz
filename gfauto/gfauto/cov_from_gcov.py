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

"""Processes .gcda and .gcno files to get a .cov file.

Unlike most code in gfauto, we use str instead of pathlib.Path because the increased speed is probably worthwhile.
"""

import argparse
import os
import pickle
import shutil
import sys
from typing import Optional

from gfauto import cov_util


def main() -> None:

    parser = argparse.ArgumentParser(
        description="Processes .gcda and .gcno files to get a .cov file that contains the line coverage data.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument(
        "--gcov_path", type=str, help="Path to gcov", default=shutil.which("gcov")
    )

    parser.add_argument(
        "--gcov_uses_json",
        help="Pass to indicate that your gcov version is 9+ and so uses the newer JSON intermediate format. "
        "This is faster, but you must have gcc 9+.",
        action="store_true",
    )

    parser.add_argument(
        "--num_threads",
        type=int,
        help="Thead pool size for running gcov in parallel.",
        default=8,
    )

    parser.add_argument(
        "--out", type=str, help="The output .cov file", default="output.cov"
    )

    parser.add_argument(
        "build_dir",
        type=str,
        help="The build directory where the compiler was invoked.",
    )

    parser.add_argument(
        "--gcov_prefix_dir",
        type=str,
        default=None,
        help="The GCOV_PREFIX directory that was used when running the target application. "
        'If the directory ends with "PROC_ID" then "PROC_ID" will be replaced with each directory that exists '
        'and the coverage results will be merged. E.g. Given "--gcov_prefix_dir /cov/PROC_ID", the results from '
        "/cov/001 /cov/002 /cov/blah etc. will be computed and the results will be merged.",
    )

    parser.add_argument(
        "--gcov_functions",
        help="Pass to indicate that the output measures coverage of functions (instead of lines)."
        "This requires using --gcov_uses_json and you must have gcc 9+.",
        action="store_true",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    if not parsed_args.gcov_path:
        parser.error("Please provide gcov_path")

    if parsed_args.gcov_functions and not parsed_args.gcov_uses_json:
        parser.error("Function coverage requires using --gcov_uses_json with gcc 9+.")

    gcov_path: str = parsed_args.gcov_path

    build_dir = parsed_args.build_dir
    build_dir = os.path.abspath(build_dir)
    build_dir = os.path.normpath(build_dir)

    gcov_prefix_dir: Optional[str] = parsed_args.gcov_prefix_dir
    if gcov_prefix_dir:
        gcov_prefix_dir = os.path.abspath(gcov_prefix_dir)
        gcov_prefix_dir = os.path.normpath(gcov_prefix_dir)

    gcov_tags = (
        ["functions", "start_line", "execution_count"]
        if parsed_args.gcov_functions
        else ["lines", "line_number", "count"]
    )

    data = cov_util.GetLineCountsData(
        gcov_path=gcov_path,
        gcov_uses_json_output=parsed_args.gcov_uses_json,
        build_dir=build_dir,
        gcov_prefix_dir=gcov_prefix_dir,
        num_threads=parsed_args.num_threads,
        gcov_json_tags=gcov_tags,
    )

    output_coverage_path: str = parsed_args.out

    # Special case for "PROC_ID".
    if gcov_prefix_dir and "PROC_ID" in gcov_prefix_dir:
        print("Detected PROC_ID in gcov_prefix_dir")
        if os.path.exists(gcov_prefix_dir):
            raise AssertionError(f"Unexpected file/directory: {gcov_prefix_dir}.")
        gcov_prefix_prefix = os.path.dirname(gcov_prefix_dir)
        if "PROC_ID" in gcov_prefix_prefix:
            raise AssertionError(
                f"Can only handle PROC_ID as the last component of the path: {gcov_prefix_dir}"
            )
        for proc_dir in os.listdir(gcov_prefix_prefix):
            proc_dir = os.path.join(gcov_prefix_prefix, proc_dir)
            if not os.path.isdir(proc_dir):
                continue
            data.gcov_prefix_dir = proc_dir
            print(f"Consuming {data.gcov_prefix_dir}")
            cov_util.get_line_counts(data)
    else:
        print(f"Consuming {data.gcov_prefix_dir}")
        cov_util.get_line_counts(data)

    with open(output_coverage_path, mode="wb") as f:
        pickle.dump(data.line_counts, f, protocol=pickle.HIGHEST_PROTOCOL)


if __name__ == "__main__":
    main()
