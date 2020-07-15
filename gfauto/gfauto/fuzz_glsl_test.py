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

"""GLSL fuzzing module.

Functions for handling GLSL shader job tests.
"""

import random
import re
import subprocess
from pathlib import Path
from typing import Iterable, List, Optional

from gfauto import (
    amber_converter,
    android_device,
    binaries_util,
    fuzz,
    fuzz_spirv_test,
    gflogging,
    glsl_generate_util,
    host_device_util,
    interrupt_util,
    result_util,
    shader_compiler_util,
    shader_job_util,
    signature_util,
    spirv_opt_util,
    subprocess_util,
    test_util,
    tool,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test, TestGlsl
from gfauto.util import check


class ReductionFailedError(Exception):
    def __init__(self, message: str, reduction_work_dir: Path):
        super().__init__(message)
        self.reduction_work_dir = reduction_work_dir


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
    unprepared_reference_shader_job: Path = random.choice(references)

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

                # Create the prepared (for Vulkan GLSL) reference.
                glsl_generate_util.run_prepare_reference(
                    graphicsfuzz_tool_path,
                    unprepared_reference_shader_job,
                    template_source_dir
                    / test_util.REFERENCE_DIR
                    / test_util.SHADER_JOB,
                    legacy_graphics_fuzz_vulkan_arg=settings.legacy_graphics_fuzz_vulkan_arg,
                )

                # Generate the variant (GraphicsFuzz requires the unprepared reference as input).
                glsl_generate_util.run_generate(
                    graphicsfuzz_tool_path,
                    unprepared_reference_shader_job,
                    donors_dir,
                    template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
                    seed=str(random.getrandbits(glsl_generate_util.GENERATE_SEED_BITS)),
                    other_args=list(settings.extra_graphics_fuzz_generate_args)
                    if settings.extra_graphics_fuzz_generate_args
                    else None,
                    legacy_graphics_fuzz_vulkan_arg=settings.legacy_graphics_fuzz_vulkan_arg,
                )
            finally:
                gflogging.pop_stream_for_logging()
    except subprocess.CalledProcessError:
        util.mkdirs_p(fuzz_failures_dir)
        if len(list(fuzz_failures_dir.iterdir())) < settings.maximum_fuzz_failures:
            util.copy_dir(staging_dir, fuzz_failures_dir / staging_dir.name)
        return

    reference_name = unprepared_reference_shader_job.stem

    stable_shader = reference_name.startswith("stable_")

    common_spirv_args = list(settings.common_spirv_args)

    test_dirs = [
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_no_opt_test",
            spirv_opt_args=None,
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_O_test",
            spirv_opt_args=["-O"],
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_Os_test",
            spirv_opt_args=["-Os"],
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand1_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand2_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
        make_test(
            template_source_dir,
            staging_dir / f"{staging_name}_opt_rand3_test",
            spirv_opt_args=spirv_opt_util.random_spirv_opt_args(),
            binary_manager=binary_manager,
            derived_from=reference_name,
            stable_shader=stable_shader,
            common_spirv_args=common_spirv_args,
        ),
    ]

    for test_dir in test_dirs:
        interrupt_util.interrupt_if_needed()
        if handle_test(test_dir, reports_dir, active_devices, binary_manager, settings):
            # If we generated a report, don't bother trying other optimization combinations.
            break


def add_spirv_shader_test_binaries(
    test: Test,
    spirv_opt_args: Optional[List[str]],
    binary_manager: binaries_util.BinaryManager,
) -> Test:
    test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-dis")])
    test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-val")])
    if spirv_opt_args:
        test.binaries.extend([binary_manager.get_binary_by_name(name="spirv-opt")])
    test.binaries.extend([binary_manager.get_binary_by_name(name="amber")])
    test.binaries.extend([binary_manager.get_binary_by_name(name="amber_apk")])
    test.binaries.extend([binary_manager.get_binary_by_name(name="amber_apk_test")])
    return test


def make_test(
    base_source_dir: Path,
    subtest_dir: Path,
    spirv_opt_args: Optional[List[str]],
    binary_manager: binaries_util.BinaryManager,
    derived_from: Optional[str],
    stable_shader: bool,
    common_spirv_args: Optional[List[str]],
) -> Path:

    source_dir = test_util.get_source_dir(subtest_dir)

    # Create the subtest by copying the base source.
    util.copy_dir(base_source_dir, source_dir)

    test = Test(
        glsl=TestGlsl(spirv_opt_args=spirv_opt_args),
        derived_from=derived_from,
        common_spirv_args=common_spirv_args,
    )

    test.binaries.extend([binary_manager.get_binary_by_name(name="glslangValidator")])
    add_spirv_shader_test_binaries(test, spirv_opt_args, binary_manager)

    # Write the test metadata.
    test_util.metadata_write(test, subtest_dir)

    # If the reference shader is "stable" (with respect to floating-point sensitivity)
    # then this can be a wrong image test; i.e. we will render the reference and variant shaders
    # and compare the output images.
    # Otherwise, we should just render the variant shader and check for crashes; to do this,
    # we just rename the `reference/` directory to `_reference/` so that the test has no reference shader.
    if not stable_shader:
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

    result_output_dir = run_shader_job(
        source_dir=test_util.get_source_dir(test_dir),
        output_dir=test_util.get_results_directory(test_dir, device.name),
        binary_manager=binary_manager,
        device=device,
        preprocessor_cache=preprocessor_cache,
    )

    return result_util.get_status(result_output_dir)


def maybe_add_report(  # pylint: disable=too-many-locals,too-many-branches;
    test_dir: Path, reports_dir: Path, device: Device, settings: Settings
) -> Optional[Path]:

    result_output_dir = test_util.get_results_directory(test_dir, device.name)

    status = result_util.get_status(result_output_dir)

    report_subdirectory_name = ""

    if status == fuzz.STATUS_CRASH:
        report_subdirectory_name = "crashes"
    elif status == fuzz.STATUS_TOOL_CRASH:
        report_subdirectory_name = "tool_crashes"
    elif status == fuzz.STATUS_UNRESPONSIVE:
        report_subdirectory_name = "unresponsive"

    if not report_subdirectory_name:
        return None
    log_path = result_util.get_log_path(result_output_dir)

    log_contents = util.file_read_text(log_path)
    signature = signature_util.get_signature_from_log_contents(log_contents)

    signature_dir = reports_dir / report_subdirectory_name / signature

    # If the signature_dir contains a NOT_INTERESTING file, then don't bother creating a report.
    if (signature_dir / "NOT_INTERESTING").is_file():
        log(
            f'Discarding test because of file: {str(signature_dir / "NOT_INTERESTING")}'
        )
        return None

    # Don't create a report for ignored signatures (specified by the device).
    ignored_signatures = set(device.ignored_crash_signatures)
    if signature in ignored_signatures:
        log(
            f"Discarding test; signature is in the device's ignored_crash_signatures: {signature}"
        )
        return None

    if signature_dir.is_dir() and signature != signature_util.BAD_IMAGE_SIGNATURE:
        # If we have reached the maximum number of crashes per signature for this device, don't create a report.
        num_duplicates = [
            report_dir
            for report_dir in signature_dir.iterdir()
            if report_dir.is_dir() and report_dir.name.endswith(f"_{device.name}")
        ]
        if len(num_duplicates) >= settings.maximum_duplicate_crashes:
            return None

    # We include the device name in the directory name because it is possible that this test crashes on two
    # different devices but gives the same crash signature in both cases (e.g. for generic signatures
    # like "compile_error"). This would lead to two test copies having the same path.
    # It also means we can limit duplicates per device using the directory name.
    test_dir_in_reports = signature_dir / f"{test_dir.name}_{device.name}"

    util.copy_dir(test_dir, test_dir_in_reports)

    if signature != signature_util.BAD_IMAGE_SIGNATURE:

        # If we found a crash, rename the directories for all shaders other than the variant. Thus, only the variant
        # shader will run.

        bad_shader_name = result_util.get_status_bad_shader_name(
            test_util.get_results_directory(test_dir_in_reports, device.name)
        )

        # TODO: Could possibly improve this. Could try scanning the Amber log to figure out which shader failed?

        if not bad_shader_name:
            log("WARNING: assuming that the bad shader is the variant")
            bad_shader_name = test_util.VARIANT_DIR

        shader_jobs = tool.get_shader_jobs(
            test_util.get_source_dir(test_dir_in_reports)
        )
        found_bad_shader = False
        for shader_job in shader_jobs:
            if shader_job.name == bad_shader_name:
                found_bad_shader = True
            else:
                shader_job.shader_job.parent.rename(
                    shader_job.shader_job.parent.parent / f"_{shader_job.name}"
                )
        check(
            found_bad_shader,
            AssertionError(
                f"Could not find bad shader at: {test_util.get_source_dir(test_dir_in_reports) / bad_shader_name}"
            ),
        )

    test_metadata = test_util.metadata_read(test_dir_in_reports)
    test_metadata.crash_signature = signature
    test_metadata.device.CopyFrom(device)
    test_metadata.expected_status = status
    test_util.metadata_write(test_metadata, test_dir_in_reports)

    return test_dir_in_reports


def should_reduce_report(settings: Settings, test_dir: Path) -> bool:
    test = test_util.metadata_read(test_dir)
    status = test.expected_status
    signature = test.crash_signature

    if not settings.reduce_tool_crashes and status == fuzz.STATUS_TOOL_CRASH:
        return False
    if (
        not settings.reduce_crashes
        and status == fuzz.STATUS_CRASH
        and signature != signature_util.BAD_IMAGE_SIGNATURE
    ):
        return False
    if (
        not settings.reduce_bad_images
        and status == fuzz.STATUS_CRASH
        and signature == signature_util.BAD_IMAGE_SIGNATURE
    ):
        return False

    if (
        settings.only_reduce_signature_regex
        and re.fullmatch(settings.only_reduce_signature_regex, signature) is None
    ):
        return False

    return True


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

        # No need to run further on real devices if the pre-processing step failed.
        if status == fuzz.STATUS_TOOL_CRASH:
            break

        # Skip devices if interrupted, but finish reductions, if needed.
        if interrupt_util.interrupted():
            break

    # For each device that saw a crash, copy the test to reports_dir, adding the signature and device info to the test
    # metadata.
    for device in active_devices:
        report_dir = maybe_add_report(test_dir, reports_dir, device, settings)
        if report_dir:
            report_paths.append(report_dir)

    # For each report, run a reduction on the target device with the device-specific crash signature.
    for test_dir_in_reports in report_paths:
        if should_reduce_report(settings, test_dir_in_reports):
            run_reduction_on_report(
                test_dir_in_reports, reports_dir, binary_manager, settings
            )
        else:
            log("Skipping reduction due to settings.")

    # For each report, create a summary and reproduce the bug.
    for test_dir_in_reports in report_paths:
        fuzz.create_summary_and_reproduce(test_dir_in_reports, binary_manager, settings)

    return issue_found


def run_shader_job(  # pylint: disable=too-many-return-statements,too-many-branches, too-many-locals, too-many-statements;
    source_dir: Path,
    output_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    test: Optional[Test] = None,
    device: Optional[Device] = None,
    ignore_test_and_device_binaries: bool = False,
    shader_job_overrides: Iterable[tool.NameAndShaderJob] = (),
    shader_job_shader_overrides: Optional[
        tool.ShaderJobNameToShaderOverridesMap
    ] = None,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> Path:

    if not shader_job_shader_overrides:
        shader_job_shader_overrides = {}

    with util.file_open_text(output_dir / "log.txt", "w") as log_file:
        try:
            gflogging.push_stream_for_logging(log_file)

            # TODO: If Amber is going to be used, check if Amber can use Vulkan debug layers now, and if not, pass that
            #  info down via a bool.

            if not test:
                test = test_util.metadata_read_from_path(
                    source_dir / test_util.TEST_METADATA
                )

            if not device:
                device = test.device

            log(f"Running test on device:\n{device.name}")

            # We will create a binary_manager child with a restricted set of binaries so that we only use the binaries
            # specified in the test and by the device; if some required binaries are not specified by the test nor the
            # device, there will be an error instead of falling back to our default binaries. But we keep a reference to
            # the parent so we can still access certain "test-independent" binaries like Amber.

            binary_manager_parent = binary_manager

            if not ignore_test_and_device_binaries:
                binary_manager = binary_manager.get_child_binary_manager(
                    list(device.binaries) + list(test.binaries)
                )

            spirv_opt_hash: Optional[str] = None
            spirv_opt_args: Optional[List[str]] = None
            if test.glsl.spirv_opt_args or test.spirv_fuzz.spirv_opt_args:
                spirv_opt_hash = binary_manager.get_binary_by_name(
                    binaries_util.SPIRV_OPT_NAME
                ).version
                spirv_opt_args = (
                    list(test.glsl.spirv_opt_args)
                    if test.glsl.spirv_opt_args
                    else list(test.spirv_fuzz.spirv_opt_args)
                )

            shader_jobs = tool.get_shader_jobs(
                source_dir, overrides=shader_job_overrides
            )

            combined_spirv_shader_jobs: List[tool.SpirvCombinedShaderJob] = []

            for shader_job in shader_jobs:
                try:
                    shader_overrides = shader_job_shader_overrides.get(
                        shader_job.name, None
                    )
                    combined_spirv_shader_jobs.append(
                        tool.compile_shader_job(
                            name=shader_job.name,
                            input_json=shader_job.shader_job,
                            work_dir=output_dir / shader_job.name,
                            binary_paths=binary_manager,
                            spirv_opt_args=spirv_opt_args,
                            shader_overrides=shader_overrides,
                            preprocessor_cache=preprocessor_cache,
                            skip_validation=test.skip_validation,
                            common_spirv_args=list(test.common_spirv_args),
                        )
                    )
                except subprocess.CalledProcessError:
                    result_util.write_status(
                        output_dir, fuzz.STATUS_TOOL_CRASH, shader_job.name
                    )
                    return output_dir
                except subprocess.TimeoutExpired:
                    result_util.write_status(
                        output_dir, fuzz.STATUS_TOOL_TIMEOUT, shader_job.name
                    )
                    return output_dir

            # Device types: |preprocess| and |shader_compiler| don't need an AmberScript file.

            # noinspection PyTypeChecker
            if device.HasField("preprocess"):
                # The "preprocess" device type just needs to get this far, so this is a success.
                result_util.write_status(output_dir, fuzz.STATUS_SUCCESS)
                return output_dir

            # noinspection PyTypeChecker
            if device.HasField("shader_compiler"):
                for combined_spirv_shader_job in combined_spirv_shader_jobs:
                    try:
                        shader_compiler_util.run_shader_job(
                            device.shader_compiler,
                            combined_spirv_shader_job.spirv_shader_job,
                            output_dir,
                            binary_manager=binary_manager,
                        )
                    except subprocess.CalledProcessError:
                        result_util.write_status(
                            output_dir,
                            fuzz.STATUS_CRASH,
                            combined_spirv_shader_job.name,
                        )
                        return output_dir
                    except subprocess.TimeoutExpired:
                        result_util.write_status(
                            output_dir,
                            fuzz.STATUS_TIMEOUT,
                            combined_spirv_shader_job.name,
                        )
                        return output_dir

                # The shader compiler succeeded on all files; this is a success.
                result_util.write_status(output_dir, fuzz.STATUS_SUCCESS)
                return output_dir

            # Other device types need an AmberScript file.

            amber_converter_shader_job_files = [
                amber_converter.ShaderJobFile(
                    name_prefix=combined_spirv_shader_job.name,
                    asm_spirv_shader_job_json=combined_spirv_shader_job.spirv_asm_shader_job,
                    glsl_source_json=combined_spirv_shader_job.glsl_source_shader_job,
                    processing_info="",
                )
                for combined_spirv_shader_job in combined_spirv_shader_jobs
            ]

            # Check if the first is the reference shader; if so, pull it out into its own variable.

            reference: Optional[amber_converter.ShaderJobFile] = None
            variants = amber_converter_shader_job_files

            if (
                amber_converter_shader_job_files[0].name_prefix
                == test_util.REFERENCE_DIR
            ):
                reference = amber_converter_shader_job_files[0]
                variants = variants[1:]
            elif len(variants) > 1:
                raise AssertionError(
                    "More than one variant, but no reference. This is unexpected."
                )

            amber_script_file = amber_converter.spirv_asm_shader_job_to_amber_script(
                shader_job_file_amber_test=amber_converter.ShaderJobFileBasedAmberTest(
                    reference_asm_spirv_job=reference, variants_asm_spirv_job=variants
                ),
                output_amber_script_file_path=output_dir / "test.amber",
                amberfy_settings=amber_converter.AmberfySettings(
                    spirv_opt_args=spirv_opt_args, spirv_opt_hash=spirv_opt_hash
                ),
            )

            is_compute = bool(
                shader_job_util.get_related_files(
                    combined_spirv_shader_jobs[0].spirv_shader_job,
                    [shader_job_util.EXT_COMP],
                )
            )

            # noinspection PyTypeChecker
            if device.HasField("host") or device.HasField("swift_shader"):
                icd: Optional[Path] = None

                # noinspection PyTypeChecker
                if device.HasField("swift_shader"):
                    icd = binary_manager.get_binary_path_by_name(
                        binaries_util.SWIFT_SHADER_NAME
                    ).path

                custom_launcher: Optional[List[str]] = None

                if device.HasField("host"):
                    custom_launcher = list(device.host.custom_launcher)

                # Run the test on the host using Amber.
                host_device_util.run_amber(
                    amber_script_file,
                    output_dir,
                    amber_path=binary_manager_parent.get_binary_path_by_name(
                        binaries_util.AMBER_NAME
                    ).path,
                    dump_image=(not is_compute),
                    dump_buffer=is_compute,
                    icd=icd,
                    custom_launcher=custom_launcher,
                )
                return output_dir

            # noinspection PyTypeChecker
            if device.HasField("android"):

                android_device.run_amber_on_device(
                    amber_script_file,
                    output_dir,
                    dump_image=(not is_compute),
                    dump_buffer=is_compute,
                    serial=device.android.serial,
                )
                return output_dir

            # TODO: For a remote device (which we will probably need to support), use log_a_file to output the
            #  "amber_log.txt" file.

            raise AssertionError(f"Unhandled device type:\n{str(device)}")

        finally:
            gflogging.pop_stream_for_logging()


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

    reduction_work_variant_dir = run_glsl_reduce(
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

    return test_util.get_source_dir(reduction_part_output_dir)


def run_glsl_reduce(
    source_dir: Path,
    name_of_shader_to_reduce: str,
    output_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    preserve_semantics: bool = False,
    extra_args: Optional[List[str]] = None,
) -> Path:

    input_shader_job = source_dir / name_of_shader_to_reduce / test_util.SHADER_JOB

    glsl_reduce_path = util.tool_on_path(
        "glsl-reduce",
        str(binary_manager.get_binary_path_by_name("graphicsfuzz-tool").path.parent),
    )

    cmd = [
        str(glsl_reduce_path),
        str(input_shader_job),
        "--output",
        str(output_dir),
    ]

    if preserve_semantics:
        cmd.append("--preserve-semantics")

    if extra_args:
        cmd.extend(extra_args)

    cmd.extend(
        [
            # This ensures the arguments that follow are all positional arguments.
            "--",
            "gfauto_interestingness_test",
            str(source_dir),
            # --override_shader_job requires two parameters to follow; the second will be added by glsl-reduce (the shader.json file).
            "--override_shader_job",
            str(name_of_shader_to_reduce),
        ]
    )

    # Log the reduction.
    with util.file_open_text(output_dir / "command.log", "w") as f:
        gflogging.push_stream_for_logging(f)
        try:
            # The reducer can fail, but it will typically output an exception file, so we can ignore the exit code.
            subprocess_util.run(cmd, verbose=True, check_exit_code=False)
        finally:
            gflogging.pop_stream_for_logging()

    return output_dir


def create_summary_and_reproduce(  # pylint: disable=too-many-locals;
    test_dir: Path, binary_manager: binaries_util.BinaryManager, settings: Settings
) -> None:
    test_metadata = test_util.metadata_read(test_dir)

    summary_dir = test_dir / "summary"

    summary_source_dirs: List[Path] = []

    unreduced = util.copy_dir(
        test_util.get_source_dir(test_dir), summary_dir / "unreduced"
    )
    summary_source_dirs.append(unreduced)

    # For the `summary/reduced_1/` directory.
    reduction_output_dir_1 = test_util.get_reduced_test_dir(
        test_dir, test_metadata.device.name, "1"
    )
    reduced_1: Optional[Path] = None
    if reduction_output_dir_1.is_dir():
        reduction_output_source_dir_1 = test_util.get_source_dir(reduction_output_dir_1)
        if reduction_output_source_dir_1.is_dir():
            reduced_1 = util.copy_dir(
                reduction_output_source_dir_1, summary_dir / "reduced_1"
            )
            summary_source_dirs.append(reduced_1)

    # For the `summary/reduced_2/` directory.
    reduction_output_dir_2 = test_util.get_reduced_test_dir(
        test_dir, test_metadata.device.name, "2"
    )
    if reduction_output_dir_2.is_dir():
        reduction_output_source_dir_2 = test_util.get_source_dir(reduction_output_dir_2)
        if reduction_output_source_dir_2.is_dir():
            reduced_2 = util.copy_dir(
                reduction_output_source_dir_2, summary_dir / "reduced_2"
            )
            summary_source_dirs.append(reduced_2)

    # If this test was generated from a stable shader...
    if test_metadata.derived_from.startswith("stable_") and reduced_1:
        # Before running the reduced_1 source dir, find any renamed shader jobs (e.g. reference/ -> _reference/)
        # and rename them back. Thus, the modified test becomes a wrong image test once again, even though
        # the actual bug was probably a crash bug.
        renamed_shader_jobs = list(reduced_1.glob("_*"))
        renamed_shader_jobs = [
            r for r in renamed_shader_jobs if (r / test_util.SHADER_JOB).is_file()
        ]
        if renamed_shader_jobs:
            for renamed_shader_job in renamed_shader_jobs:
                util.move_dir(
                    renamed_shader_job,
                    renamed_shader_job.with_name(renamed_shader_job.name[1:]),
                )

        # Also, if this is a spirv_fuzz test then try to create a variant_2 shader job that is even more similar to the
        # variant than the reference shader job.
        if test_metadata.HasField("spirv_fuzz"):
            fuzz_spirv_test.create_spirv_fuzz_variant_2(
                reduced_1, binary_manager, settings
            )

    # Run every source dir that we added to the summary dir.
    for summary_source_dir in summary_source_dirs:
        run_shader_job(
            source_dir=summary_source_dir,
            output_dir=(summary_dir / f"{summary_source_dir.name}_result"),
            binary_manager=binary_manager,
        )


def tool_crash_summary_bug_report_dir(  # pylint: disable=too-many-locals;
    source_dir: Path,
    result_dir: Path,
    output_dir: Path,
    binary_manager: binaries_util.BinaryManager,
) -> Optional[Path]:
    # Create a simple script and README.

    shader_job = source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB

    if not shader_job.is_file():
        return None

    test_metadata: Test = test_util.metadata_read_from_path(
        source_dir / test_util.TEST_METADATA
    )

    shader_files = shader_job_util.get_related_files(
        shader_job,
        shader_job_util.EXT_ALL,
        (shader_job_util.SUFFIX_GLSL, shader_job_util.SUFFIX_SPIRV),
    )
    check(
        len(shader_files) > 0,
        AssertionError(f"Need at least one shader for {shader_job}"),
    )

    shader_extension = shader_files[0].suffix

    bug_report_dir = util.copy_dir(result_dir, output_dir / "bug_report")

    # Create bug_report.zip.
    zip_files = [
        util.ZipEntry(f, f.relative_to(bug_report_dir))
        for f in sorted(bug_report_dir.rglob("*"))
    ]
    util.create_zip(bug_report_dir.with_suffix(".zip"), zip_files)

    shader_files = sorted(bug_report_dir.rglob("shader.*"))

    glsl_files = [
        shader_file
        for shader_file in shader_files
        if shader_file.suffix == shader_extension
    ]

    asm_files = [
        shader_file
        for shader_file in shader_files
        if shader_file.name.endswith(
            shader_extension + shader_job_util.SUFFIX_ASM_SPIRV
        )
    ]

    spv_files = [
        shader_file
        for shader_file in shader_files
        if shader_file.name.endswith(shader_extension + shader_job_util.SUFFIX_SPIRV)
    ]

    readme = "\n\n"
    readme += (
        "Issue found using [GraphicsFuzz](https://github.com/google/graphicsfuzz).\n\n"
    )
    readme += "Tool versions:\n\n"

    # noinspection PyTypeChecker
    if test_metadata.HasField("glsl"):
        readme += f"* glslangValidator commit hash: {binary_manager.get_binary_by_name(binaries_util.GLSLANG_VALIDATOR_NAME).version}\n"

    if test_metadata.glsl.spirv_opt_args or test_metadata.spirv_fuzz.spirv_opt_args:
        readme += f"* spirv-opt commit hash: {binary_manager.get_binary_by_name(binaries_util.SPIRV_OPT_NAME).version}\n"

    readme += "\nTo reproduce:\n\n"
    readme += f"`glslangValidator -V shader{shader_extension} -o shader{shader_extension}.spv`\n\n"

    if (
        test_metadata.HasField("glsl")
        and spv_files
        and not test_metadata.glsl.spirv_opt_args
    ):
        # GLSL was converted to SPIR-V, and spirv-opt was not run, so indicate that we should validate the SPIR-V.
        readme += f"`spirv-val shader{shader_extension}.spv`\n\n"

    if test_metadata.glsl.spirv_opt_args or test_metadata.spirv_fuzz.spirv_opt_args:
        readme += f"`spirv-opt shader{shader_extension}.spv -o temp.spv --validate-after-all {' '.join(test_metadata.glsl.spirv_opt_args)}`\n\n"

    files_to_list = glsl_files + spv_files + asm_files
    files_to_list.sort()

    files_to_show = glsl_files + asm_files
    files_to_show.sort()

    readme += "The following shader files are included in the attached archive, some of which are also shown inline below:\n\n"

    for file_to_list in files_to_list:
        short_path = file_to_list.relative_to(bug_report_dir).as_posix()
        readme += f"* {short_path}\n"

    for file_to_show in files_to_show:
        short_path = file_to_show.relative_to(bug_report_dir).as_posix()
        file_contents = util.file_read_text(file_to_show)
        readme += f"\n{short_path}:\n\n"
        readme += f"```\n{file_contents}\n```\n"

    util.file_write_text(output_dir / "README.md", readme)

    return bug_report_dir
