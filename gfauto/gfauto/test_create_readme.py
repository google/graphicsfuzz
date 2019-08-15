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

"""Create readme module.

A script that creates a README and bug_report directory for a test and its result_dir.
"""

import argparse
import sys
from pathlib import Path

from gfauto import artifact_util, binaries_util, fuzz_glsl_test, test_util
from gfauto.binaries_util import BinaryManager
from gfauto.util import check, check_dir_exists


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A script that creates a README and bug_report directory for a test and its result_dir."
    )

    parser.add_argument(
        "source_dir", help="Source directory containing test.json and shaders."
    )

    parser.add_argument(
        "result_dir",
        help="Path to the result_dir of a test containing e.g. the intermediate shader files, log.txt, etc.",
    )

    parser.add_argument(
        "--output_dir",
        help="Output directory where the README and bug_report directory will be written.",
        default=".",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    source_dir = Path(parsed_args.source_dir)
    result_dir = Path(parsed_args.result_dir)
    output_dir = Path(parsed_args.output_dir)

    check_dir_exists(source_dir)
    check_dir_exists(result_dir)

    artifact_util.recipes_write_built_in()

    binary_manager = BinaryManager(binaries_util.DEFAULT_BINARIES)

    test = test_util.metadata_read_from_path(source_dir / test_util.TEST_METADATA)

    check(test.HasField("glsl"), AssertionError("Only glsl tests currently supported"))

    check(
        test.device.HasField("preprocess"),
        AssertionError("Only preprocess device tests currently supported"),
    )

    fuzz_glsl_test.tool_crash_summary_bug_report_dir(
        source_dir, result_dir, output_dir, binary_manager
    )


if __name__ == "__main__":
    main()
    sys.exit(0)
