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

"""Outputs the "new coverage" given A.cov (baseline) and B.cov.

Unlike most code in gfauto, we use str instead of pathlib.Path because the increased speed is probably worthwhile.
"""

import argparse
import pickle
import sys

from gfauto import cov_util


def main() -> None:

    parser = argparse.ArgumentParser(
        description='Outputs the "new coverage" from A.cov (baseline) to B.cov. '
        "The output coverage file will only include the counts from B.cov for newly-covered lines; "
        "all other lines will have a count of zero.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("a_coverage", type=str, help="The baseline A.cov file.")
    parser.add_argument("b_coverage", type=str, help="The B.cov file.")
    parser.add_argument("output_coverage", type=str, help="The output .cov file.")

    parsed_args = parser.parse_args(sys.argv[1:])

    a_coverage: str = parsed_args.a_coverage
    b_coverage: str = parsed_args.b_coverage
    output_coverage: str = parsed_args.output_coverage

    with open(a_coverage, mode="rb") as f:
        a_line_counts: cov_util.LineCounts = pickle.load(f)

    with open(b_coverage, mode="rb") as f:
        b_line_counts: cov_util.LineCounts = pickle.load(f)

    found_new: bool = False

    # We modify b_line_counts so that lines already covered by A are set to 0.
    # Note that line counts appear to be able to overflow, so we use "!= 0" instead of "> 0".
    for source_file_path, b_counts in b_line_counts.items():
        if source_file_path in a_line_counts:
            a_counts = a_line_counts[source_file_path]
            newly_covered: int = 0
            for line_number, b_count in b_counts.items():
                if b_count != 0:
                    # Defaults to 0 if not present.
                    if a_counts[line_number] != 0:
                        b_counts[line_number] = 0
                    else:
                        newly_covered += 1
                        found_new = True
            if newly_covered > 0:
                print("+" + "{:03d}".format(newly_covered) + " line(s) newly covered in " + source_file_path)

    with open(output_coverage, mode="wb") as f:
        pickle.dump(b_line_counts, f, protocol=pickle.HIGHEST_PROTOCOL)

if __name__ == "__main__":
    main()
