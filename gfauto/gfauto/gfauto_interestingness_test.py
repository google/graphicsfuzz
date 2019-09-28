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

"""Interestingness test for gfauto.

When running a reducer, this module is used as the interestingness test.
An interestingness test runs a test on a device and returns 0 if the test
still exposes the behavior of interest (e.g. a crash, an incorrectly rendered image).

See |setup.py| to see how this module is added to the entry_points/console_scripts.
"""

import argparse
import sys
from pathlib import Path
from typing import List, Optional, Tuple

from gfauto import (
    artifact_util,
    binaries_util,
    fuzz,
    fuzz_glsl_test,
    result_util,
    signature_util,
    test_util,
    tool,
    util,
)
from gfauto.gflogging import log

# TODO: Maybe add helper method and throw exceptions instead of calling sys.exit.

# TODO: Could we make the interestingness test the only way of running a shader job (or indeed, any test)?
#  We would want to pass the output directory (default is one will be created, as is currently the case), the test_json,
#  no crash signature nor device (we can get that from the test_json), although perhaps these could be overridden?
#  A device (or test?) could then even specify a custom interestingness command, although the default one would probably
#  be the same for all devices and it would look at the device info in the test_json?


def main() -> None:  # pylint: disable=too-many-statements;
    parser = argparse.ArgumentParser(
        description="Interestingness test that runs a test using Amber, "
        "calculates the crash signature based on the result, and returns 0 "
        "if the signature matches the expected crash signature."
    )

    parser.add_argument(
        "source_dir",
        help="The source directory containing the shaders and the test.json file that describes how to run the test.",
    )
    parser.add_argument(
        "--override_shader_job",
        nargs=2,
        metavar=("shader_job_name", "shader_job_json"),
        help='Override one of the shader jobs. E.g.: "--override_shader_job variant temp/variant.json". Note that '
        "the output directory will be set to shader_job_json/ (with the .json extension removed) by default in this case. ",
    )

    parser.add_argument(
        "--override_shader",
        nargs=3,
        metavar=("shader_name", "suffix", "shader_path"),
        help='Override one of the shaders. E.g.: "--override_shader variant .frag temp/my_shader.spv". Note that '
        "the output directory will be set to shader_path/ (with the .spv extension removed) by default in this case. ",
    )

    parser.add_argument(
        "--use_default_binaries",
        help="Use the latest binaries, ignoring those defined in the test.json. "
        "Implies --fallback_binaries.",
        action="store_true",
    )

    parser.add_argument(
        "--fallback_binaries",
        help="Fallback to the latest binaries if they are not defined in the test.json.",
        action="store_true",
    )

    parser.add_argument(
        "--output",
        help="Output directory. Required unless --override_shader[_job] is used; see --override_shader[_job] for details.",
        default=None,
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    source_dir: Path = Path(parsed_args.source_dir)
    override_shader_job: Optional[Tuple[str, str]] = parsed_args.override_shader_job
    override_shader: Optional[Tuple[str, str, str]] = parsed_args.override_shader

    use_default_binaries: bool = parsed_args.use_default_binaries
    fallback_binaries: bool = parsed_args.fallback_binaries or use_default_binaries
    output: Path
    if parsed_args.output:
        output = Path(parsed_args.output)
    elif override_shader_job:
        output = Path(override_shader_job[1]).with_suffix("")
    elif override_shader:
        output = Path(override_shader[2]).with_suffix("")
    else:
        raise AssertionError("Need --output or --override_shader[_job] parameter.")

    # artifact_util.recipes_write_built_in()

    binary_manager = binaries_util.BinaryManager(
        binaries_util.DEFAULT_BINARIES if fallback_binaries else [],
        util.get_platform(),
        binaries_util.BUILT_IN_BINARY_RECIPES_PATH_PREFIX,
    )

    shader_job_overrides: List[tool.NameAndShaderJob] = []

    if override_shader_job:
        shader_job_overrides.append(
            tool.NameAndShaderJob(
                name=override_shader_job[0], shader_job=Path(override_shader_job[1])
            )
        )

    # We don't need to read this to run the shader, but we need it afterwards anyway.
    test = test_util.metadata_read_from_path(source_dir / test_util.TEST_METADATA)

    output_dir = fuzz_glsl_test.run_shader_job(
        source_dir=source_dir,
        output_dir=output,
        binary_manager=binary_manager,
        test=test,
        ignore_test_and_device_binaries=use_default_binaries,
        shader_job_overrides=shader_job_overrides,
    )

    log(
        f"gfauto_interestingness_test: finished running {str(source_dir)} in {str(output_dir)}."
    )

    if override_shader_job:
        log(
            f"The {override_shader_job[0]} shader was overridden with {override_shader_job[1]}"
        )

    status = result_util.get_status(output_dir)
    if test.expected_status:
        log("")
        log(f"Expected status: {test.expected_status}")
        log(f"Actual   status: {status}")

    log_contents = util.file_read_text(result_util.get_log_path(output_dir))
    signature = signature_util.get_signature_from_log_contents(log_contents)

    log("")
    log(f"Expected signature: {test.crash_signature}")
    log(f"Actual   signature: {signature}")

    log("")

    if test.expected_status:
        if status != test.expected_status:
            log("status != expected_status; not interesting")
            sys.exit(1)
    else:
        # There is no expected status given, so just assume it needs to be one of the "bad" statuses:
        if status not in (
            fuzz.STATUS_CRASH,
            fuzz.STATUS_TOOL_CRASH,
            fuzz.STATUS_UNRESPONSIVE,
        ):
            log("shader run did not fail; not interesting.")
            sys.exit(1)

    if signature != test.crash_signature:
        log("signature != crash_signature; not interesting")
        sys.exit(1)

    log("Interesting!")


if __name__ == "__main__":
    main()
    sys.exit(0)
