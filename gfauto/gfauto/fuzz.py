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
import enum
import os
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
    download_cts_gf_tests,
    fuzz_glsl_amber_test,
    fuzz_spirv_amber_test,
    fuzz_test_util,
    gflogging,
    interrupt_util,
    run_cts_gf_tests,
    settings_util,
    shader_job_util,
    test_util,
    util,
)
from gfauto.device_pb2 import Device, DevicePreprocess
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
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
DONORS_DIR = "donors"
REFERENCES_DIR = "references"

SPIRV_DONORS_DIR = "spirv_fuzz_donors"
SPIRV_REFERENCES_DIR = "spirv_fuzz_references"

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


class FuzzingTool(enum.Enum):
    GLSL_FUZZ = "GLSL_FUZZ"
    SPIRV_FUZZ = "SPIRV_FUZZ"


def get_random_name() -> str:
    # TODO: could change to human-readable random name or the date.
    return util.get_random_name()


def get_fuzzing_tool_pattern(
    glsl_fuzz_iterations: int, spirv_fuzz_iterations: int
) -> List[FuzzingTool]:

    fuzzing_tool_pattern = [FuzzingTool.GLSL_FUZZ] * glsl_fuzz_iterations
    fuzzing_tool_pattern += [FuzzingTool.SPIRV_FUZZ] * spirv_fuzz_iterations

    # If empty, we default to just running GLSL_FUZZ repeatedly.
    if not fuzzing_tool_pattern:
        fuzzing_tool_pattern = [FuzzingTool.GLSL_FUZZ]

    return fuzzing_tool_pattern


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fuzz devices using glsl-fuzz and/or spirv-fuzz to generate tests. "
        "By default, repeatedly generates tests using glsl-fuzz. "
        "You can instead specify the number of times each tool will run; "
        "glsl-fuzz runs G times, then spirv-fuzz runs S times, then the pattern repeats. "
        "By default, G=0 and S=0, in which case glsl-fuzz is hardcoded to run. "
        'Each run of glsl-fuzz/spirv-fuzz uses a random "iteration seed", which can be used to replay the invocation of the tool and the steps that follow. ',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

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
        "--glsl_fuzz_iterations",
        metavar="G",
        help="Run glsl-fuzz G times to generate some tests, before moving on to the next tool.",
        action="store",
        default=0,
        type=int,
    )

    parser.add_argument(
        "--spirv_fuzz_iterations",
        metavar="S",
        help="Run spirv-fuzz S times to generate some tests, before moving on to the next tool.",
        action="store",
        default=0,
        type=int,
    )

    parser.add_argument(
        "--allow_no_stack_traces",
        help="Continue even if we cannot get stack traces (using catchsegv or cdb).",
        action="store_true",
    )

    parser.add_argument(
        "--active_device",
        help="Add an active device name, overriding those in the settings.json file. "
        "Ignored when --update_ignored_crash_signatures is passed."
        "Can be used multiple times to add multiple devices. "
        "E.g. --active_device host --active_device host_with_alternative_icd. "
        "This allows sharing a single settings.json file between multiple instances of gfauto_fuzz. "
        "Note that a host_preprocessor device will automatically be added as the first active device, if it is missing. ",
        action="append",
    )

    parser.add_argument(
        "--update_ignored_crash_signatures",
        metavar="GERRIT_COOKIE",
        help="When passed, gfauto will download and run the existing GraphicsFuzz AmberScript tests from Khronos vk-gl-cts on "
        "the active devices listed in the settings.json file. "
        "It will then update the ignored_crash_signatures field for each active device in the settings.json file based on the crash signatures seen. "
        "Requires Git. Requires Khronos membership. Obtain the Gerrit cookie as follows. "
        + download_cts_gf_tests.GERRIT_COOKIE_INSTRUCTIONS,
        action="store",
        default=None,
        type=str,
    )

    parser.add_argument(
        "--iteration_limit",
        help="Stop after this many fuzzing iterations.",
        action="store",
        default=None,
        type=int,
    )

    parser.add_argument(
        "--keep_temp",
        help="Keep temp directories. Useful for debugging with --iteration_seed.",
        action="store_true",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    settings_path = Path(parsed_args.settings)
    iteration_seed: Optional[int] = None if parsed_args.iteration_seed is None else int(
        parsed_args.iteration_seed
    )
    glsl_fuzz_iterations: int = parsed_args.glsl_fuzz_iterations
    spirv_fuzz_iterations: int = parsed_args.spirv_fuzz_iterations
    allow_no_stack_traces: bool = parsed_args.allow_no_stack_traces
    active_device_names: Optional[List[str]] = parsed_args.active_device
    update_ignored_crash_signatures_gerrit_cookie: Optional[str] = (
        parsed_args.update_ignored_crash_signatures
    )
    iteration_limit: Optional[int] = parsed_args.iteration_limit
    keep_temp: bool = parsed_args.keep_temp

    # E.g. [GLSL_FUZZ, GLSL_FUZZ, SPIRV_FUZZ] will run glsl-fuzz twice, then spirv-fuzz once, then repeat.
    fuzzing_tool_pattern = get_fuzzing_tool_pattern(
        glsl_fuzz_iterations=glsl_fuzz_iterations,
        spirv_fuzz_iterations=spirv_fuzz_iterations,
    )

    with util.file_open_text(Path(f"log_{get_random_name()}.txt"), "w") as log_file:
        gflogging.push_stream_for_logging(log_file)
        try:
            main_helper(
                settings_path,
                iteration_seed,
                fuzzing_tool_pattern,
                allow_no_stack_traces,
                active_device_names=active_device_names,
                update_ignored_crash_signatures_gerrit_cookie=update_ignored_crash_signatures_gerrit_cookie,
                iteration_limit=iteration_limit,
                keep_temp=keep_temp,
            )
        except settings_util.NoSettingsFile as exception:
            log(str(exception))
        finally:
            gflogging.pop_stream_for_logging()


def try_get_root_file() -> Path:
    try:
        return artifact_util.artifact_path_get_root()
    except FileNotFoundError:
        log(
            "Could not find ROOT file (in the current directory or above) to mark where binaries should be stored. "
            "Creating a ROOT file in the current directory."
        )
        return util.file_write_text(Path(artifact_util.ARTIFACT_ROOT_FILE_NAME), "")


def main_helper(  # pylint: disable=too-many-locals, too-many-branches, too-many-statements;
    settings_path: Path,
    iteration_seed_override: Optional[int] = None,
    fuzzing_tool_pattern: Optional[List[FuzzingTool]] = None,
    allow_no_stack_traces: bool = False,
    override_sigint: bool = True,
    use_amber_vulkan_loader: bool = False,
    active_device_names: Optional[List[str]] = None,
    update_ignored_crash_signatures_gerrit_cookie: Optional[str] = None,
    iteration_limit: Optional[int] = None,
    keep_temp: bool = False,
) -> None:

    if not fuzzing_tool_pattern:
        fuzzing_tool_pattern = [FuzzingTool.GLSL_FUZZ]

    util.update_gcov_environment_variable_if_needed()

    if override_sigint:
        interrupt_util.override_sigint()

    try_get_root_file()

    settings = settings_util.read_or_create(settings_path)

    binary_manager = binaries_util.get_default_binary_manager(settings=settings)

    temp_dir = Path() / "temp"

    # Note: we use "is not None" so that if the user passes an empty Gerrit cookie, we still try to execute this code.
    if update_ignored_crash_signatures_gerrit_cookie is not None:
        git_tool = util.tool_on_path("git")
        downloaded_graphicsfuzz_tests_dir = (
            temp_dir / f"graphicsfuzz_cts_tests_{get_random_name()[:8]}"
        )
        work_dir = temp_dir / f"graphicsfuzz_cts_run_{get_random_name()[:8]}"
        download_cts_gf_tests.download_cts_graphicsfuzz_tests(
            git_tool=git_tool,
            cookie=update_ignored_crash_signatures_gerrit_cookie,
            output_tests_dir=downloaded_graphicsfuzz_tests_dir,
        )
        download_cts_gf_tests.extract_shaders(
            tests_dir=downloaded_graphicsfuzz_tests_dir, binaries=binary_manager
        )
        with util.file_open_text(work_dir / "results.csv", "w") as results_out_handle:
            run_cts_gf_tests.main_helper(
                tests_dir=downloaded_graphicsfuzz_tests_dir,
                work_dir=work_dir,
                binaries=binary_manager,
                settings=settings,
                active_devices=devices_util.get_active_devices(settings.device_list),
                results_out_handle=results_out_handle,
                updated_settings_output_path=settings_path,
            )
        return

    active_devices = devices_util.get_active_devices(
        settings.device_list, active_device_names=active_device_names
    )

    # Add host_preprocessor device from device list if it is missing.
    if not active_devices[0].HasField("preprocess"):
        for device in settings.device_list.devices:
            if device.HasField("preprocess"):
                active_devices.insert(0, device)
                break

    # Add host_preprocessor device (from scratch) if it is still missing.
    if not active_devices[0].HasField("preprocess"):
        active_devices.insert(
            0, Device(name="host_preprocessor", preprocess=DevicePreprocess())
        )

    reports_dir = Path() / "reports"
    fuzz_failures_dir = reports_dir / FUZZ_FAILURES_DIR_NAME
    references_dir = Path() / REFERENCES_DIR
    donors_dir = Path() / DONORS_DIR
    spirv_fuzz_references_dir = Path() / SPIRV_REFERENCES_DIR
    spirv_fuzz_donors_dir = Path() / SPIRV_DONORS_DIR

    # Log a warning if there is no tool on the PATH for printing stack traces.
    prepended = util.prepend_catchsegv_if_available([], log_warning=True)
    if not allow_no_stack_traces and not prepended:
        raise AssertionError("Stopping because we cannot get stack traces.")

    spirv_fuzz_reference_shaders: List[Path] = []
    spirv_fuzz_donor_shaders: List[Path] = []
    references: List[Path] = []

    if FuzzingTool.SPIRV_FUZZ in fuzzing_tool_pattern:
        check_dir_exists(spirv_fuzz_references_dir)
        check_dir_exists(spirv_fuzz_donors_dir)
        spirv_fuzz_reference_shaders = sorted(spirv_fuzz_references_dir.rglob("*.json"))
        spirv_fuzz_donor_shaders = sorted(spirv_fuzz_donors_dir.rglob("*.json"))

    if FuzzingTool.GLSL_FUZZ in fuzzing_tool_pattern:
        check_dir_exists(references_dir)
        check_dir_exists(donors_dir)
        # TODO: make GraphicsFuzz find donors recursively.
        references = sorted(references_dir.rglob("*.json"))
        # Filter to only include .json files that have at least one shader (.frag, .vert, .comp) file.
        references = [
            ref for ref in references if shader_job_util.get_related_files(ref)
        ]

    if use_amber_vulkan_loader:
        library_path = binary_manager.get_binary_path_by_name(
            binaries_util.AMBER_VULKAN_LOADER_NAME
        ).path.parent
        util.add_library_paths_to_environ([library_path], os.environ)

    fuzzing_tool_index = 0
    iteration_count = 0

    while True:

        # We use "is not None" because iteration_limit could be 0.
        if iteration_limit is not None and iteration_count >= iteration_limit:
            log(f"Stopping after {iteration_count} iterations.")
            break

        interrupt_util.interrupt_if_needed()

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
            log("Starting new iteration.")
            continue

        # Pseudocode:
        #  - Create test_dir(s) in staging directory.
        #  - Run test_dir(s) on all active devices (stop early if appropriate).
        #  - For each test failure on each device, copy the test to reports_dir, adding the device and crash signature.
        #  - Reduce each report (on the given device).
        #  - Produce a summary for each report.

        fuzzing_tool = fuzzing_tool_pattern[fuzzing_tool_index]
        fuzzing_tool_index = (fuzzing_tool_index + 1) % len(fuzzing_tool_pattern)

        if fuzzing_tool == FuzzingTool.SPIRV_FUZZ:
            fuzz_spirv_amber_test.fuzz_spirv(
                staging_dir,
                reports_dir,
                fuzz_failures_dir,
                active_devices,
                spirv_fuzz_reference_shaders,
                spirv_fuzz_donor_shaders,
                settings,
                binary_manager,
            )
        elif fuzzing_tool == FuzzingTool.GLSL_FUZZ:
            fuzz_glsl_amber_test.fuzz_glsl(
                staging_dir,
                reports_dir,
                fuzz_failures_dir,
                active_devices,
                references,
                donors_dir,
                settings,
                binary_manager,
            )
        else:
            raise AssertionError(f"Unknown fuzzing tool: {fuzzing_tool}")

        if not keep_temp:
            shutil.rmtree(staging_dir)

        if iteration_seed_override is not None:
            log("Stopping due to iteration_seed")
            break

        iteration_count += 1


def create_summary_and_reproduce(
    test_dir: Path, binary_manager: binaries_util.BinaryManager, settings: Settings,
) -> None:
    util.mkdirs_p(test_dir / "summary")
    test_metadata = test_util.metadata_read(test_dir)

    # noinspection PyTypeChecker
    if test_metadata.HasField("glsl") or test_metadata.HasField("spirv_fuzz"):
        fuzz_test_util.create_summary_and_reproduce(test_dir, binary_manager, settings)
    else:
        raise AssertionError("Unrecognized test type")


if __name__ == "__main__":
    main()
