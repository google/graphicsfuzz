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

"""SPIR-V fuzzing module.

Functions for handling spirv-fuzz generated tests.
"""

import random
import subprocess
from pathlib import Path
from typing import List, Optional

from gfauto import (
    binaries_util,
    fuzz,
    fuzz_glsl_test,
    result_util,
    shader_job_util,
    spirv_fuzz_util,
    spirv_opt_util,
    test_util,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test, TestSpirvFuzz


def make_test(
    base_source_dir: Path,
    subtest_dir: Path,
    spirv_opt_args: Optional[List[str]],
    binary_manager: binaries_util.BinaryManager,
) -> Path:
    # Create the subtest by copying the base source.
    util.copy_dir(base_source_dir, test_util.get_source_dir(subtest_dir))

    test = Test(spirv_fuzz=TestSpirvFuzz(spirv_opt_args=spirv_opt_args))

    # TODO: Handle spirv_opt_args.

    test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-dis")])
    test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-val")])
    if spirv_opt_args:
        test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-opt")])

    # Write the test metadata.
    test_util.metadata_write(test, subtest_dir)

    return subtest_dir


def run(
    test_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    device: Optional[Device] = None,
) -> str:

    test: Test = test_util.metadata_read(test_dir)
    if not device:
        device = test.device

    result_output_dir = fuzz_glsl_test.run_shader_job(
        source_dir=test_util.get_source_dir(test_dir),
        output_dir=test_util.get_results_directory(test_dir, device.name),
        binary_manager=binary_manager,
        device=device,
    )

    return result_util.get_status(result_output_dir)


def handle_test(
    test_dir: Path,
    reports_dir: Path,
    active_devices: List[Device],
    binary_manager: binaries_util.BinaryManager,
    settings: Settings,
) -> bool:
    report_paths: List[Path] = []
    issue_found = False

    # Run on all devices.
    for device in active_devices:
        status = run(test_dir, binary_manager, device)
        if status in (
            fuzz.STATUS_CRASH,
            fuzz.STATUS_TOOL_CRASH,
            fuzz.STATUS_UNRESPONSIVE,
        ):
            issue_found = True
        if status == fuzz.STATUS_TOOL_CRASH:
            # No need to run further on real devices if the pre-processing step failed.
            break

    # For each device that saw a crash, copy the test to reports_dir, adding the signature and device info to the test
    # metadata.
    for device in active_devices:
        report_dir = fuzz_glsl_test.maybe_add_report(
            test_dir, reports_dir, device, settings
        )
        if report_dir:
            report_paths.append(report_dir)

    # TODO: reductions.

    # TODO: summaries.

    return issue_found


def fuzz_spirv(
    staging_dir: Path,
    reports_dir: Path,
    fuzz_failures_dir: Path,
    active_devices: List[Device],
    spirv_fuzz_shaders: List[Path],
    settings: Settings,
    binary_manager: binaries_util.BinaryManager,
) -> None:

    staging_name = staging_dir.name
    template_source_dir = staging_dir / "source_template"

    # Copy in a randomly chosen reference.
    reference_spirv_shader_job = shader_job_util.copy(
        random.choice(spirv_fuzz_shaders),
        template_source_dir / test_util.REFERENCE_DIR / test_util.SHADER_JOB,
        language_suffix=shader_job_util.SUFFIXES_SPIRV_FUZZ_INPUT,
    )

    # TODO: Allow using downloaded spirv-fuzz.
    try:
        spirv_fuzz_util.run_generate_on_shader_job(
            util.tool_on_path("spirv-fuzz"),
            reference_spirv_shader_job,
            template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
            seed=str(random.getrandbits(spirv_fuzz_util.GENERATE_SEED_BITS)),
        )
    except subprocess.CalledProcessError:
        util.mkdirs_p(fuzz_failures_dir)
        if len(list(fuzz_failures_dir.iterdir())) < settings.maximum_fuzz_failures:
            util.copy_dir(staging_dir, fuzz_failures_dir / staging_dir.name)
        return

    test_dirs = [
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_no_opt_test",
            spirv_opt_args=None,
            binary_manager=binary_manager,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_O_test",
            spirv_opt_args=["-O"],
            binary_manager=binary_manager,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_Os_test",
            spirv_opt_args=["-Os"],
            binary_manager=binary_manager,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand1_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand2_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand3_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
        ),
    ]

    for test_dir in test_dirs:
        if handle_test(test_dir, reports_dir, active_devices, binary_manager, settings):
            # If we generated a report, don't bother trying other optimization combinations.
            break
