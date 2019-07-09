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

import argparse
import sys
from pathlib import Path

from gfauto import (
    built_in_binaries,
    fuzz,
    fuzz_glsl_test,
    result_util,
    signature_util,
    test_util,
    util,
)
from gfauto.gflogging import log
from gfauto.util import check, check_file_exists

# TODO: Maybe add helper method and throw exceptions instead of calling sys.exit.

# TODO: Could we make the interestingness test the only way of running a shader job (or indeed, any test)?
#  We would want to pass the output directory (default is one will be created, as is currently the case), the test_json,
#  no crash signature nor device (we can get that from the test_json), although perhaps these could be overridden?
#  A device (or test?) could then even specify a custom interestingness command, although the default one would probably
#  be the same for all devices and it would look at the device info in the test_json?


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Interestingness test that runs a shader using Amber, "
        "calculates the crash signature based on the result, and returns 0 "
        "if the signature matches the expected crash signature."
    )

    parser.add_argument(
        "test_json",
        help="The .json test metadata file path that describes how to run the test.",
    )
    parser.add_argument("shader_job_json", help="The .json shader job file path.")

    parsed_args = parser.parse_args(sys.argv[1:])

    test_json: Path = Path(parsed_args.test_json)
    shader_job_json: Path = Path(parsed_args.shader_job_json)

    check_file_exists(test_json)
    check_file_exists(shader_job_json)

    test = test_util.metadata_read_from_path(test_json)

    check(
        test.HasField("glsl") and bool(test.device) and bool(test.crash_signature),
        AssertionError(
            f"Provided test json {str(test_json)} does not have entries: glsl, device, crash_signature"
        ),
    )

    binary_manager = built_in_binaries.BinaryManager(
        [], util.get_platform(), built_in_binaries.BUILT_IN_BINARY_RECIPES_PATH_PREFIX
    )

    output_dir = fuzz_glsl_test.run_shader_job(
        shader_job_json,
        output_dir=shader_job_json.with_suffix(""),
        test=test,
        device=test.device,
        binary_manager=binary_manager,
    )

    log(
        f"gfauto_interestingness_test: finished running {str(shader_job_json)} in {str(output_dir)}."
    )

    status = result_util.get_status(output_dir)
    if status not in (fuzz.STATUS_CRASH, fuzz.STATUS_TOOL_CRASH):
        log("Shader run did not crash; not interesting.")
        sys.exit(1)

    log_contents = util.file_read_text(result_util.get_log_path(output_dir))
    signature = signature_util.get_signature_from_log_contents(log_contents)

    log(f"Expected signature: {test.crash_signature}")
    log(f"Actual   signature: {signature}")

    if signature == test.crash_signature:
        log("Interesting!")
        return

    log("Not interesting")
    sys.exit(1)


if __name__ == "__main__":
    main()
    sys.exit(0)
