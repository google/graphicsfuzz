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
    gflogging,
    result_util,
    shader_job_util,
    signature_util,
    spirv_fuzz_util,
    spirv_opt_util,
    subprocess_util,
    test_util,
    tool,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.fuzz_glsl_test import ReductionFailedError
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test, TestSpirvFuzz
from gfauto.util import check


def make_test(
    base_source_dir: Path,
    subtest_dir: Path,
    spirv_opt_args: Optional[List[str]],
    binary_manager: binaries_util.BinaryManager,
) -> Path:
    # Create the subtest by copying the base source.
    util.copy_dir(base_source_dir, test_util.get_source_dir(subtest_dir))

    test = Test(spirv_fuzz=TestSpirvFuzz(spirv_opt_args=spirv_opt_args))

    fuzz_glsl_test.add_spirv_shader_test_binaries(test, spirv_opt_args, binary_manager)

    # Write the test metadata.
    test_util.metadata_write(test, subtest_dir)

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

    result_output_dir = fuzz_glsl_test.run_shader_job(
        source_dir=test_util.get_source_dir(test_dir),
        output_dir=test_util.get_results_directory(test_dir, device.name),
        binary_manager=binary_manager,
        device=device,
        preprocessor_cache=preprocessor_cache,
    )

    return result_util.get_status(result_output_dir)


def run_spirv_reduce_or_shrink(
    source_dir: Path,
    name_of_shader_job_to_reduce: str,
    extension_to_reduce: str,
    output_dir: Path,
    preserve_semantics: bool,
    binary_manager: binaries_util.BinaryManager,
) -> Path:
    input_shader_job = source_dir / name_of_shader_job_to_reduce / test_util.SHADER_JOB

    original_spirv_file = input_shader_job.with_suffix(
        extension_to_reduce + shader_job_util.SUFFIX_SPIRV_ORIG
    )

    transformed_spirv_file = input_shader_job.with_suffix(
        extension_to_reduce + shader_job_util.SUFFIX_SPIRV
    )
    transformations_file = input_shader_job.with_suffix(
        extension_to_reduce + shader_job_util.SUFFIX_TRANSFORMATIONS
    )

    util.mkdirs_p(output_dir)

    final_shader = output_dir / "final.spv"

    # E.g. transformation_suffix_to_reduce == ".frag.transformations"

    # E.g. ".frag.??" -> ".frag.spv"
    shader_suffix_to_override = extension_to_reduce + shader_job_util.SUFFIX_SPIRV

    if preserve_semantics:
        cmd = [
            str(binary_manager.get_binary_path_by_name("spirv-fuzz").path),
            str(original_spirv_file),
            "-o",
            str(final_shader),
            f"--shrink={str(transformations_file)}",
            f"--shrinker-temp-file-prefix={str(output_dir / 'temp_')}",
            # This ensures the arguments that follow are all positional arguments.
            "--",
            "gfauto_interestingness_test",
            str(source_dir),
            # --override_shader requires three parameters to follow; the third will be added by spirv-fuzz (the shader.spv file).
            "--override_shader",
            name_of_shader_job_to_reduce,
            shader_suffix_to_override,
        ]
    else:
        cmd = [
            str(binary_manager.get_binary_path_by_name("spirv-reduce").path),
            str(transformed_spirv_file),
            "-o",
            str(final_shader),
            f"--temp-file-prefix={str(output_dir / 'temp_')}",
            # This ensures the arguments that follow are all positional arguments.
            "--",
            "gfauto_interestingness_test",
            str(source_dir),
            # --override_shader requires three parameters to follow; the third will be added by spirv-reduce (the shader.spv file).
            "--override_shader",
            name_of_shader_job_to_reduce,
            shader_suffix_to_override,
        ]

    # Log the reduction.
    with util.file_open_text(output_dir / "command.log", "w") as f:
        gflogging.push_stream_for_logging(f)
        try:
            # The reducer can fail, but it will typically output an exception file, so we can ignore the exit code.
            subprocess_util.run(cmd, verbose=True, check_exit_code=False)
        finally:
            gflogging.pop_stream_for_logging()

    return final_shader


def run_reduction(
    test_dir_reduction_output: Path,
    test_dir_to_reduce: Path,
    shader_job_name_to_reduce: str,
    extension_to_reduce: str,
    preserve_semantics: bool,
    binary_manager: binaries_util.BinaryManager,
    reduction_name: str = "reduction1",
) -> Path:
    test = test_util.metadata_read(test_dir_to_reduce)

    check(
        bool(test.device and test.device.name),
        AssertionError(
            f"Cannot reduce {str(test_dir_to_reduce)}; "
            f"device must be specified in {str(test_util.get_metadata_path(test_dir_to_reduce))}"
        ),
    )

    check(
        bool(test.crash_signature),
        AssertionError(
            f"Cannot reduce {str(test_dir_to_reduce)} because there is no crash string specified."
        ),
    )

    # E.g. reports/crashes/no_signature/d50c96e8_opt_rand2_test_phone_ABC/results/phone_ABC/reductions/1
    # Will contain work/ and source/
    reduced_test_dir = test_util.get_reduced_test_dir(
        test_dir_reduction_output, test.device.name, reduction_name
    )

    source_dir = test_util.get_source_dir(test_dir_to_reduce)
    output_dir = test_util.get_reduction_work_directory(
        reduced_test_dir, shader_job_name_to_reduce
    )
    if preserve_semantics:
        final_shader_path = run_spirv_reduce_or_shrink(
            source_dir=source_dir,
            name_of_shader_job_to_reduce=shader_job_name_to_reduce,
            extension_to_reduce=extension_to_reduce,
            output_dir=output_dir,
            preserve_semantics=preserve_semantics,
            binary_manager=binary_manager,
        )
    else:
        final_shader_path = run_spirv_reduce_or_shrink(
            source_dir=source_dir,
            name_of_shader_job_to_reduce=shader_job_name_to_reduce,
            extension_to_reduce=extension_to_reduce,
            output_dir=output_dir,
            preserve_semantics=preserve_semantics,
            binary_manager=binary_manager,
        )

    check(
        final_shader_path.exists(),
        ReductionFailedError("Reduction failed.", reduction_name, output_dir),
    )

    # Finally, create the source_dir so the returned directory can be used as a test_dir.

    # Copy the original source directory.
    util.copy_dir(source_dir, test_util.get_source_dir(reduced_test_dir))

    # And then replace the shader.

    # Destination file. E.g. reductions/source/variant/shader.frag.spv
    output_shader_prefix = (
        test_util.get_source_dir(reduced_test_dir)
        / shader_job_name_to_reduce
        / test_util.SHADER_JOB
    ).with_suffix(extension_to_reduce + shader_job_util.SUFFIX_SPIRV)

    util.copy_file(
        final_shader_path.with_suffix(shader_job_util.SUFFIX_SPIRV),
        output_shader_prefix.with_suffix(shader_job_util.SUFFIX_SPIRV),
    )

    if preserve_semantics:
        util.copy_file(
            final_shader_path.with_suffix(shader_job_util.SUFFIX_TRANSFORMATIONS),
            output_shader_prefix.with_suffix(shader_job_util.SUFFIX_TRANSFORMATIONS),
        )

        util.copy_file(
            final_shader_path.with_suffix(shader_job_util.SUFFIX_TRANSFORMATIONS_JSON),
            output_shader_prefix.with_suffix(
                shader_job_util.SUFFIX_TRANSFORMATIONS_JSON
            ),
        )

    return reduced_test_dir


def run_reduction_on_report(  # pylint: disable=too-many-locals;
    test_dir: Path, reports_dir: Path, binary_manager: binaries_util.BinaryManager
) -> None:
    test = test_util.metadata_read(test_dir)

    check(
        bool(test.device and test.device.name),
        AssertionError(
            f"Cannot reduce {str(test_dir)}; "
            f"device must be specified in {str(test_util.get_metadata_path(test_dir))}"
        ),
    )

    check(
        bool(test.crash_signature),
        AssertionError(
            f"Cannot reduce {str(test_dir)} because there is no crash string specified."
        ),
    )

    source_dir = test_util.get_source_dir(test_dir)

    shader_jobs = tool.get_shader_jobs(source_dir)

    # TODO: if needed, this could become a parameter to this function.
    shader_job_to_reduce = shader_jobs[0]

    if len(shader_jobs) > 1:
        check(
            len(shader_jobs) == 2 and shader_jobs[1].name == test_util.VARIANT_DIR,
            AssertionError(
                "Can only reduce tests with shader jobs reference and variant, or just variant."
            ),
        )
        shader_job_to_reduce = shader_jobs[1]

    shader_transformation_suffixes = shader_job_util.get_related_suffixes_that_exist(
        shader_job_to_reduce.shader_job,
        language_suffix=(shader_job_util.SUFFIX_TRANSFORMATIONS,),
    )

    shader_spv_suffixes = shader_job_util.get_related_suffixes_that_exist(
        shader_job_to_reduce.shader_job, language_suffix=(shader_job_util.SUFFIX_SPIRV,)
    )

    try:
        reduced_test = test_dir

        for index, suffix in enumerate(shader_transformation_suffixes):
            # E.g. .frag.transformations -> .frag
            extension_to_reduce = str(Path(suffix).with_suffix(""))
            reduced_test = run_reduction(
                test_dir_reduction_output=test_dir,
                test_dir_to_reduce=reduced_test,
                shader_job_name_to_reduce=shader_job_to_reduce.name,
                extension_to_reduce=extension_to_reduce,
                preserve_semantics=True,
                binary_manager=binary_manager,
                reduction_name=f"0_{index}_{suffix.split('.')[1]}",
            )

        if test.crash_signature != signature_util.BAD_IMAGE_SIGNATURE:
            for index, suffix in enumerate(shader_spv_suffixes):
                # E.g. .frag.spv -> .frag
                extension_to_reduce = str(Path(suffix).with_suffix(""))
                reduced_test = run_reduction(
                    test_dir_reduction_output=test_dir,
                    test_dir_to_reduce=reduced_test,
                    shader_job_name_to_reduce=shader_job_to_reduce.name,
                    extension_to_reduce=extension_to_reduce,
                    preserve_semantics=False,
                    binary_manager=binary_manager,
                    reduction_name=f"1_{index}_{suffix.split('.')[1]}",
                )

        device_name = test.device.name

        # Create a symlink to the "best" reduction.
        best_reduced_test_link = test_util.get_reduced_test_dir(
            test_dir, device_name, fuzz.BEST_REDUCTION_NAME
        )
        util.make_directory_symlink(
            new_symlink_file_path=best_reduced_test_link, existing_dir=reduced_test
        )
    except ReductionFailedError as ex:
        # Create a symlink to the failed reduction so it is easy to investigate failed reductions.
        link_to_failed_reduction_path = (
            reports_dir / "failed_reductions" / f"{test_dir.name}_{ex.reduction_name}"
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
) -> bool:
    report_paths: List[Path] = []
    issue_found = False
    preprocessor_cache = util.CommandCache()

    # Run on all devices.
    for device in active_devices:
        status = run(
            test_dir, binary_manager, device, preprocessor_cache=preprocessor_cache
        )
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

    # For each report, run a reduction on the target device with the device-specific crash signature.
    for test_dir_in_reports in report_paths:
        if fuzz_glsl_test.should_reduce_report(settings, test_dir_in_reports):
            run_reduction_on_report(
                test_dir_in_reports, reports_dir, binary_manager=binary_manager
            )
        else:
            log("Skipping reduction due to settings.")

    # For each report, create a summary and reproduce the bug.
    for test_dir_in_reports in report_paths:
        fuzz.create_summary_and_reproduce(test_dir_in_reports, binary_manager)

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
        with util.file_open_text(staging_dir / "log.txt", "w") as log_file:
            try:
                gflogging.push_stream_for_logging(log_file)
                spirv_fuzz_util.run_generate_on_shader_job(
                    binary_manager.get_binary_path_by_name("spirv-fuzz").path,
                    reference_spirv_shader_job,
                    template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
                    seed=str(random.getrandbits(spirv_fuzz_util.GENERATE_SEED_BITS)),
                )
            finally:
                gflogging.pop_stream_for_logging()
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
