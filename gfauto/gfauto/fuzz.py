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

"""Fuzzing module.

The main entry point to GraphicsFuzz Auto.
"""

import argparse
import random
import secrets
import shutil
import sys
from pathlib import Path
from typing import List, Optional

from gfauto import (
    artifact_util,
    binaries_util,
    devices_util,
    fuzz_glsl_test,
    fuzz_spirv_test,
    gflogging,
    settings_util,
    shader_job_util,
    test_util,
    util,
)
from gfauto.device_pb2 import Device, DevicePreprocess
from gfauto.gflogging import log
from gfauto.util import check_dir_exists

# Root:
#   - donors/ (contains GLSL shader jobs)
#   - temp/ (contains staging directories with random names)
#   - reports/ (contains reports)

# Staging directory.
#  - source_template/ (source directory but with no test metadata yet)
#  - test_1/, test_2/, etc. (test directories)

# Test directory:
#  - source/ (source directory, with test.json and other files)
#  - results/ (results)

# Report: a test directory with a reduction for a specific device.
# E.g.            v signature        v device name added
# reports/crashes/Null_point/123_opt_pixel/
#   - source/
#   - results/
#     - laptop/
#       - reference/ variant/
#         - ... (see below for a more detailed example)
#     - pixel/
#       - reference/ variant/
#         - ... (see below for a more detailed example)
#       - reductions/ (since this is a report for a pixel device, we have reductions)
#         - ... (see below for a more detailed example)


# - temp/123/ (a staging directory; not a proper test_dir, as it only has "source_template", not "source".)
#   - source_template/
#     - --test.json-- this will NOT be present because this is just a source template directory.
#     - reference/ variant/
#       - shader.json, shader.{comp,frag}
#   - 123_no_opt/ 123_opt_O/ 123_opt_Os/ 123_opt_rand_1/ etc. (Proper test_dirs, as they have "source". These may be
#                                                              copied to become a report if a bug is found.)
#     - source/ (same as source_template, but with test.json)
#     - results/
#       - pixel/ other_phone/ laptop/ etc.
#         - reference/ variant/
#           - test.amber
#           - image.png
#           - STATUS
#           - log.txt
#           - (all other result files and intermediate files for running the shader on the device)
#         - reductions/ (reductions are only added once the staging directory is copied to the reports directory)
#           - reduction_1/ reduction_blah/ etc. (reduction name; also a test_dir)
#             - source/ (same as other source dirs, but with the final reduced shader source)
#             - reduction_work/
#               - reference/ variant/
#                 - shader.json, shader_reduction_001_success.json,
#                 shader_reduction_002_failed.json, etc., shader_reduced_final.json
#                 - shader/ shader_reduction_001/
#                 (these are the result directories for each step, containing STATUS, etc.)
#


REFERENCE_IMAGE_FILE_NAME = "reference.png"
VARIANT_IMAGE_FILE_NAME = "variant.png"
BUFFER_FILE_NAME = "buffer.bin"

BEST_REDUCTION_NAME = "best"

AMBER_RUN_TIME_LIMIT = 30

STATUS_TOOL_CRASH = "TOOL_CRASH"
STATUS_CRASH = "CRASH"
STATUS_UNRESPONSIVE = "UNRESPONSIVE"
STATUS_TOOL_TIMEOUT = "TOOL_TIMEOUT"
STATUS_TIMEOUT = "TIMEOUT"
STATUS_SUCCESS = "SUCCESS"

# Number of bits for seeding the RNG.
# Python normally uses 256 bits internally when seeding its RNG, hence this choice.
ITERATION_SEED_BITS = 256

FUZZ_FAILURES_DIR_NAME = "fuzz_failures"


def get_random_name() -> str:
    # TODO: could change to human-readable random name or the date.
    return util.get_random_name()


def main() -> None:
    parser = argparse.ArgumentParser(description="Fuzz")

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this fuzzing instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "--iteration_seed",
        help="The seed to use for one fuzzing iteration (useful for reproducing an issue).",
    )

    parser.add_argument(
        "--use_spirv_fuzz",
        help="Do fuzzing using spirv-fuzz, which must be on your PATH.",
        action="store_true",
    )

    parser.add_argument(
        "--force_no_stack_traces",
        help="Continue even if we cannot get stack traces (using catchsegv or cdb).",
        action="store_true",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    settings_path = Path(parsed_args.settings)
    iteration_seed: Optional[int] = None if parsed_args.iteration_seed is None else int(
        parsed_args.iteration_seed
    )
    use_spirv_fuzz: bool = parsed_args.use_spirv_fuzz
    force_no_stack_traces: bool = parsed_args.force_no_stack_traces

    with util.file_open_text(Path(f"log_{get_random_name()}.txt"), "w") as log_file:
        gflogging.push_stream_for_logging(log_file)
        try:
            main_helper(
                settings_path, iteration_seed, use_spirv_fuzz, force_no_stack_traces
            )
        except settings_util.NoSettingsFile as exception:
            log(str(exception))
        finally:
            gflogging.pop_stream_for_logging()


def main_helper(  # pylint: disable=too-many-locals, too-many-branches, too-many-statements;
    settings_path: Path,
    iteration_seed_override: Optional[int],
    use_spirv_fuzz: bool,
    force_no_stack_traces: bool,
) -> None:

    util.update_gcov_environment_variable_if_needed()

    try:
        artifact_util.artifact_path_get_root()
    except FileNotFoundError:
        log(
            "Could not find ROOT file (in the current directory or above) to mark where binaries should be stored. "
            "Creating a ROOT file in the current directory."
        )
        util.file_write_text(Path(artifact_util.ARTIFACT_ROOT_FILE_NAME), "")

    settings = settings_util.read_or_create(settings_path)

    active_devices = devices_util.get_active_devices(settings.device_list)
    # Add host_preprocessor device if it is missing.
    if not active_devices[0].HasField("preprocess"):
        active_devices.insert(
            0, Device(name="host_preprocessor", preprocess=DevicePreprocess())
        )

    reports_dir = Path() / "reports"
    fuzz_failures_dir = reports_dir / FUZZ_FAILURES_DIR_NAME
    temp_dir = Path() / "temp"
    references_dir = Path() / "references"
    donors_dir = Path() / "donors"
    spirv_fuzz_shaders_dir = Path() / "spirv_fuzz_shaders"

    # Log a warning if there is no tool on the PATH for printing stack traces.
    prepended = util.prepend_catchsegv_if_available([], log_warning=True)
    if not force_no_stack_traces and not prepended:
        raise AssertionError("Stopping because we cannot get stack traces.")

    spirv_fuzz_shaders: List[Path] = []
    references: List[Path] = []

    if use_spirv_fuzz:
        check_dir_exists(spirv_fuzz_shaders_dir)
        spirv_fuzz_shaders = sorted(spirv_fuzz_shaders_dir.rglob("*.json"))
    else:
        check_dir_exists(references_dir)
        check_dir_exists(donors_dir)
        # TODO: make GraphicsFuzz find donors recursively.
        references = sorted(references_dir.rglob("*.json"))
        # Filter to only include .json files that have at least one shader (.frag, .vert, .comp) file.
        references = [
            ref for ref in references if shader_job_util.get_related_files(ref)
        ]

    binary_manager = binaries_util.get_default_binary_manager(
        settings=settings
    ).get_child_binary_manager(list(settings.custom_binaries), prepend=True)

    while True:

        # We have to use "is not None" because the seed could be 0.
        if iteration_seed_override is not None:
            iteration_seed = iteration_seed_override
        else:
            iteration_seed = secrets.randbits(ITERATION_SEED_BITS)

        log(f"Iteration seed: {iteration_seed}")
        random.seed(iteration_seed)

        staging_name = get_random_name()[:8]
        staging_dir = temp_dir / staging_name

        try:
            util.mkdir_p_new(staging_dir)
        except FileExistsError:
            if iteration_seed_override is not None:
                raise
            log(f"Staging directory already exists: {str(staging_dir)}")
            log(f"Starting new iteration.")
            continue

        # Pseudocode:
        #  - Create test_dir(s) in staging directory.
        #  - Run test_dir(s) on all active devices (stop early if appropriate).
        #  - For each test failure on each device, copy the test to reports_dir, adding the device and crash signature.
        #  - Reduce each report (on the given device).
        #  - Produce a summary for each report.

        if use_spirv_fuzz:
            fuzz_spirv_test.fuzz_spirv(
                staging_dir,
                reports_dir,
                fuzz_failures_dir,
                active_devices,
                spirv_fuzz_shaders,
                settings,
                binary_manager,
            )
        else:
            fuzz_glsl_test.fuzz_glsl(
                staging_dir,
                reports_dir,
                fuzz_failures_dir,
                active_devices,
                references,
                donors_dir,
                settings,
                binary_manager,
            )

        shutil.rmtree(staging_dir)

        if iteration_seed_override is not None:
            log("Stopping due to iteration_seed")
            break


def create_summary_and_reproduce(
    test_dir: Path, binary_manager: binaries_util.BinaryManager
) -> None:
    util.mkdirs_p(test_dir / "summary")
    test_metadata = test_util.metadata_read(test_dir)

    # noinspection PyTypeChecker
    if test_metadata.HasField("glsl") or test_metadata.HasField("spirv_fuzz"):
        fuzz_glsl_test.create_summary_and_reproduce(test_dir, binary_manager)
    else:
        raise AssertionError("Unrecognized test type")


if __name__ == "__main__":
    main()
