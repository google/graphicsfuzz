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

"""GLSL fuzzing module, targeting ShaderTrap.

Functions for handling GLSL shader job tests when targeting OpenGL (ES) devices via ShaderTrap.
"""

import random
import shutil
import subprocess
from pathlib import Path
from typing import List, Optional

from gfauto import (
    binaries_util,
    fuzz,
    fuzz_test_util,
    gflogging,
    glsl_generate_util,
    glsl_reduce_util,
    interrupt_util,
    result_util,
    shader_job_util,
    signature_util,
    subprocess_util,
    test_util,
    tool,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.fuzz_test_util import ReductionFailedError
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test, TestGlslShaderTrap
from gfauto.util import check


def fuzz_glsl(  # pylint: disable=too-many-locals;
    staging_dir: Path,
    reports_dir: Path,
    fuzz_failures_dir: Path,
    active_devices: List[Device],
    references: List[Path],
    donors_dir: Path,
    settings: Settings,
    binary_manager: binaries_util.BinaryManager,
) -> None:
    staging_name = staging_dir.name
    template_source_dir = staging_dir / "source_template"

    # Pick a randomly chosen reference.
    reference_shader_job: Path = random.choice(references)

    # The "graphicsfuzz-tool" tool is designed to be on your PATH so that e.g. ".bat" will be appended on Windows.
    # So we use tool_on_path with a custom PATH to get the actual file we want to execute.
    graphicsfuzz_tool_path = util.tool_on_path(
        "graphicsfuzz-tool",
        str(binary_manager.get_binary_path_by_name("graphicsfuzz-tool").path.parent),
    )

    try:
        with util.file_open_text(staging_dir / "log.txt", "w") as log_file:
            try:
                gflogging.push_stream_for_logging(log_file)

                # Generate the variant.
                glsl_generate_util.run_generate(
                    graphicsfuzz_tool_path,
                    reference_shader_job,
                    donors_dir,
                    template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
                    seed=str(random.getrandbits(glsl_generate_util.GENERATE_SEED_BITS)),
                    other_args=list(settings.extra_graphics_fuzz_generate_args)
                    if settings.extra_graphics_fuzz_generate_args
                    else None,
                    target_vulkan=False,
                )
            finally:
                gflogging.pop_stream_for_logging()
    except subprocess.CalledProcessError:
        util.mkdirs_p(fuzz_failures_dir)
        if len(list(fuzz_failures_dir.iterdir())) < settings.maximum_fuzz_failures:
            util.copy_dir(staging_dir, fuzz_failures_dir / staging_dir.name)
        return

    reference_name = reference_shader_job.stem

    stable_shader = reference_name.startswith("stable_")

    test_dir = make_test(
        template_source_dir,
        staging_dir / f"{staging_name}_test",
        binary_manager=binary_manager,
        derived_from=reference_name,
        stable_shader=stable_shader,
    )

    interrupt_util.interrupt_if_needed()
    handle_test(test_dir, reports_dir, active_devices, binary_manager, settings)


def make_test(
    base_source_dir: Path,
    subtest_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    derived_from: Optional[str],
    stable_shader: bool,
) -> Path:

    source_dir = test_util.get_source_dir(subtest_dir)

    # Create the subtest by copying the base source.
    util.copy_dir(base_source_dir, source_dir)

    test = Test(glsl_shader_trap=TestGlslShaderTrap(), derived_from=derived_from,)

    # TODO(afd): Do we want this? When targeting ShaderTrap we would like to validate each shader using glslangValidator
    #  just to be sure that we are not passing invalid stuff to devices. But otherwise we do not need glslangValidator.
    test.binaries.extend([binary_manager.get_binary_by_name(name="glslangValidator")])

    # Write the test metadata.
    test_util.metadata_write(test, subtest_dir)

    # If the reference shader is "stable" (with respect to floating-point sensitivity)
    # then this can be a wrong image test; i.e. we will render the reference and variant shaders
    # and compare the output images.
    # Otherwise, we should just render the variant shader and check for crashes; to do this,
    # we just rename the `reference/` directory to `_reference/` so that the test has no reference shader.
    if not stable_shader and (source_dir / test_util.REFERENCE_DIR).is_dir():
        util.move_dir(
            source_dir / test_util.REFERENCE_DIR,
            source_dir / f"_{test_util.REFERENCE_DIR}",
        )

    return subtest_dir


def run(
    test_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    device: Optional[Device] = None,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> str:

    test: Test = test_util.metadata_read(test_dir)
    if not device:
        device = test.device

    result_output_dir = fuzz_test_util.run_shader_job_shadertrap(
        source_dir=test_util.get_source_dir(test_dir),
        output_dir=test_util.get_results_directory(test_dir, device.name),
        binary_manager=binary_manager,
        device=device,
        preprocessor_cache=preprocessor_cache,
    )

    return result_util.get_status(result_output_dir)


def run_reduction(
    source_dir_to_reduce: Path,
    reduction_output_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    settings: Settings,
) -> Path:
    test = test_util.metadata_read_from_source_dir(source_dir_to_reduce)

    reduced_source_dir = source_dir_to_reduce

    reduced_source_dir = run_reduction_part(
        reduction_part_output_dir=reduction_output_dir / "1",
        source_dir_to_reduce=reduced_source_dir,
        preserve_semantics=True,
        binary_manager=binary_manager,
        settings=settings,
    )

    if (
        test.crash_signature != signature_util.BAD_IMAGE_SIGNATURE
        and not settings.skip_semantics_changing_reduction
    ):
        reduced_source_dir = run_reduction_part(
            reduction_part_output_dir=reduction_output_dir / "2",
            source_dir_to_reduce=reduced_source_dir,
            preserve_semantics=False,
            binary_manager=binary_manager,
            settings=settings,
        )

    # Create and return a symlink to the "best" reduction part directory.
    return util.make_directory_symlink(
        new_symlink_file_path=reduction_output_dir / fuzz.BEST_REDUCTION_NAME,
        existing_dir=reduced_source_dir.parent,
    )


def run_reduction_on_report(
    test_dir: Path,
    reports_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    settings: Settings,
) -> None:
    test = test_util.metadata_read(test_dir)

    if not test.device or not test.device.name:
        raise AssertionError(f"Cannot reduce {str(test_dir)}; device must be specified")

    try:
        run_reduction(
            source_dir_to_reduce=test_util.get_source_dir(test_dir),
            reduction_output_dir=test_util.get_reductions_dir(
                test_dir, test.device.name
            ),
            binary_manager=binary_manager,
            settings=settings,
        )
    except ReductionFailedError as ex:
        # Create a symlink to the failed reduction so it is easy to investigate failed reductions.
        link_to_failed_reduction_path = (
            reports_dir
            / "failed_reductions"
            / f"{test_dir.name}_{ex.reduction_work_dir.name}"
        )
        util.make_directory_symlink(
            new_symlink_file_path=link_to_failed_reduction_path,
            existing_dir=ex.reduction_work_dir,
        )


def handle_test(
    test_dir: Path,
    reports_dir: Path,
    active_devices: List[Device],
    binary_manager: binaries_util.BinaryManager,
    settings: Settings,
) -> None:

    report_paths: List[Path] = []
    preprocessor_cache = util.CommandCache()

    # Run on all devices.
    for device in active_devices:
        status = run(
            test_dir, binary_manager, device, preprocessor_cache=preprocessor_cache
        )

        # No need to run further on real devices if the validation step failed.
        if status == fuzz.STATUS_TOOL_CRASH:
            break

        # Skip devices if interrupted, but finish reductions, if needed.
        if interrupt_util.interrupted():
            break

    # For each device that saw a crash, copy the test to reports_dir, adding the signature and device info to the test
    # metadata.
    for device in active_devices:
        report_dir = fuzz_test_util.maybe_add_report(
            test_dir, reports_dir, device, settings
        )
        if report_dir:
            report_paths.append(report_dir)

    # For each report, run a reduction on the target device with the device-specific crash signature.
    for test_dir_in_reports in report_paths:
        if fuzz_test_util.should_reduce_report(settings, test_dir_in_reports):
            run_reduction_on_report(
                test_dir_in_reports, reports_dir, binary_manager, settings
            )
        else:
            log("Skipping reduction due to settings.")

    # For each report, create a summary and reproduce the bug.
    for test_dir_in_reports in report_paths:
        fuzz.create_summary_and_reproduce(test_dir_in_reports, binary_manager, settings)


def get_final_reduced_shader_job_path(reduction_work_shader_dir: Path) -> Path:
    return reduction_work_shader_dir / "shader_reduced_final.json"


def run_reduction_part(
    reduction_part_output_dir: Path,
    source_dir_to_reduce: Path,
    preserve_semantics: bool,
    binary_manager: binaries_util.BinaryManager,
    settings: Settings,
) -> Path:
    test = test_util.metadata_read_from_source_dir(source_dir_to_reduce)

    if not test.device or not test.device.name:
        raise AssertionError(
            f"Cannot reduce {str(source_dir_to_reduce)}; "
            f"device must be specified in {str(test_util.get_metadata_path_from_source_dir(source_dir_to_reduce))}"
        )

    if not test.crash_signature:
        raise AssertionError(
            f"Cannot reduce {str(source_dir_to_reduce)} because there is no crash string specified."
        )

    shader_jobs = tool.get_shader_jobs(source_dir_to_reduce)

    # TODO: if needed, this could become a parameter to this function.
    name_of_shader_to_reduce = shader_jobs[0].name

    if len(shader_jobs) > 1:
        check(
            len(shader_jobs) == 2 and shader_jobs[1].name == test_util.VARIANT_DIR,
            AssertionError(
                "Can only reduce tests with shader jobs reference and variant, or just variant."
            ),
        )
        name_of_shader_to_reduce = shader_jobs[1].name

    reduction_work_variant_dir = glsl_reduce_util.run_glsl_reduce(
        source_dir=source_dir_to_reduce,
        name_of_shader_to_reduce=name_of_shader_to_reduce,
        output_dir=test_util.get_reduction_work_directory(
            reduction_part_output_dir, name_of_shader_to_reduce
        ),
        binary_manager=binary_manager,
        preserve_semantics=preserve_semantics,
        extra_args=list(settings.extra_graphics_fuzz_reduce_args)
        if settings.extra_graphics_fuzz_reduce_args
        else None,
    )

    final_reduced_shader_job_path = get_final_reduced_shader_job_path(
        reduction_work_variant_dir
    )

    check(
        final_reduced_shader_job_path.exists(),
        ReductionFailedError("Reduction failed.", reduction_work_variant_dir),
    )

    # Finally, create the output source_dir.

    util.copy_dir(
        source_dir_to_reduce, test_util.get_source_dir(reduction_part_output_dir)
    )

    shader_job_util.copy(
        final_reduced_shader_job_path,
        test_util.get_shader_job_path(
            reduction_part_output_dir, name_of_shader_to_reduce
        ),
    )

    if not settings.keep_reduction_work:
        shutil.rmtree(reduction_work_variant_dir)

    return test_util.get_source_dir(reduction_part_output_dir)
