# -*- coding: utf-8 -*-

# Copyright 2020 The GraphicsFuzz Project Authors
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

"""A tool for running a reduction on a source directory."""

import argparse
import sys
from pathlib import Path

from gfauto import (
    binaries_util,
    fuzz_glsl_amber_test,
    fuzz_spirv_amber_test,
    settings_util,
    test_util,
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A tool for running a reduction on a source directory."
    )

    parser.add_argument(
        "source_dir",
        help="The source directory containing the shaders and the test.json file that describes how to run the test.",
    )

    parser.add_argument(
        "--output", help="Output directory.", default="reduction_output",
    )

    parser.add_argument(
        "--settings",
        help="Path to a settings JSON file for this instance.",
        default="settings.json",
    )

    parser.add_argument(
        "--literals_to_uniforms",
        action="store_true",
        help="Pass --literals-to-uniforms to glsl-reduce.",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    source_dir = Path(parsed_args.source_dir)
    output_dir = Path(parsed_args.output)
    settings = settings_util.read_or_create(Path(parsed_args.settings))
    literals_to_uniforms: bool = parsed_args.literals_to_uniforms

    binary_manager = binaries_util.get_default_binary_manager(settings=settings)

    test = test_util.metadata_read_from_source_dir(source_dir)

    if test.HasField("glsl"):
        if (
            literals_to_uniforms
            and "--literals-to-uniforms" not in settings.extra_graphics_fuzz_reduce_args
        ):
            settings.extra_graphics_fuzz_reduce_args.append("--literals-to-uniforms")

        fuzz_glsl_amber_test.run_reduction(
            source_dir_to_reduce=source_dir,
            reduction_output_dir=output_dir,
            binary_manager=binary_manager,
            settings=settings,
        )
    elif test.HasField("spirv_fuzz"):
        fuzz_spirv_amber_test.run_reduction(
            source_dir_to_reduce=source_dir,
            reduction_output_dir=output_dir,
            binary_manager=binary_manager,
            settings=settings,
        )
    else:
        raise AssertionError(f"Unknown test type: {test}")


if __name__ == "__main__":
    main()
