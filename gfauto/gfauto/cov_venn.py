# -*- coding: utf-8 -*-

# Copyright 2021 The GraphicsFuzz Project Authors
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

"""Shows how many lines are covered by each component of the Venn diagram associated with A.cov and B.cov.

Unlike most code in gfauto, we use str instead of pathlib.Path because the increased speed is probably worthwhile.
"""

import argparse
import pickle
import sys

from gfauto import cov_util


def main() -> None:

    parser = argparse.ArgumentParser(
        description="Outputs the components of the Venn diagram associated with A.cov and B.cov.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("a_coverage", type=str, help="The A.cov file.")
    parser.add_argument("b_coverage", type=str, help="The B.cov file.")

    parsed_args = parser.parse_args(sys.argv[1:])

    a_coverage: str = parsed_args.a_coverage
    b_coverage: str = parsed_args.b_coverage

    with open(a_coverage, mode="rb") as f:
        a_line_counts: cov_util.LineCounts = pickle.load(f)

    with open(b_coverage, mode="rb") as f:
        b_line_counts: cov_util.LineCounts = pickle.load(f)

    total_lines: int = 0
    count_both: int = 0
    count_neither: int = 0
    count_just_a: int = 0
    count_just_b: int = 0

    for source_file_path, b_counts in b_line_counts.items():
        if source_file_path not in a_line_counts:
            print(
                "Warning: file "
                + source_file_path
                + " present in coverage data for "
                + b_coverage
                + " but not "
                + a_coverage
                + "; results may be meaningless."
            )
        else:
            a_counts = a_line_counts[source_file_path]
            for line_number, b_count in b_counts.items():
                total_lines += 1
                a_count = a_counts[line_number]
                if a_count == 0 and b_count == 0:
                    count_neither += 1
                elif a_count != 0 and b_count != 0:
                    count_both += 1
                elif a_count != 0:
                    count_just_a += 1
                else:
                    assert b_count != 0
                    count_just_b += 1

    # Integrity check: make sure that all source files from A.cov are represented in B.cov
    for source_file_path, a_counts in a_line_counts.items():
        if source_file_path not in b_line_counts:
            print(
                "Warning: file "
                + source_file_path
                + " present in coverage data for "
                + a_coverage
                + " but not "
                + b_coverage
                + "; results may be meaningless."
            )

    assert total_lines == count_both + count_neither + count_just_a + count_just_b
    print("Total lines:                 " + "{:06d}".format(total_lines))
    print("Lines covered by both:       " + "{:06d}".format(count_both))
    print("Lines covered by neither:    " + "{:06d}".format(count_neither))
    print("Lines covered by just A.cov: " + "{:06d}".format(count_just_a))
    print("Lines covered by just B.cov: " + "{:06d}".format(count_just_b))


if __name__ == "__main__":
    main()
