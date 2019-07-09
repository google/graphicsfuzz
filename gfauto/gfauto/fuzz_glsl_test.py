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
import subprocess
from pathlib import Path
from typing import List, Optional

from gfauto import (
    android_device,
    built_in_binaries,
    fuzz,
    gflogging,
    host_device_util,
    recipe_spirv_asm_shader_job_to_amber_script,
    result_util,
    shader_job_util,
    signature_util,
    subprocess_util,
    test_util,
    tool,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test
from gfauto.util import check


class ReductionFailedError(Exception):
    def __init__(self, message: str, reduction_name: str, reduction_work_dir: Path):
        super().__init__(message)
        self.reduction_name = reduction_name
        self.reduction_work_dir = reduction_work_dir


def run(
    test_dir: Path,
    binary_manager: built_in_binaries.BinaryManager,
    device: Optional[Device] = None,
) -> str:

    test: Test = test_util.metadata_read(test_dir)
    if not device:
        device = test.device

    log(f"Running test on device:\n{device.name}")

    result_output_dir = run_shader_job(
        shader_job=test_util.get_shader_job_path(test_dir, is_variant=True),
        output_dir=test_util.get_results_directory(
            test_dir, device.name, is_variant=True
        ),
        test=test,
        device=device,
        binary_manager=binary_manager,
    )

    return result_util.get_status(result_output_dir)


def maybe_add_report(
    test_dir: Path, reports_dir: Path, device: Device, settings: Settings
) -> Optional[Path]:

    result_output_dir = test_util.get_results_directory(
        test_dir, device.name, is_variant=True
    )

    status = result_util.get_status(result_output_dir)

    report_subdirectory_name = ""

    if status == fuzz.STATUS_CRASH:
        report_subdirectory_name = "crashes"
    elif status == fuzz.STATUS_TOOL_CRASH:
        report_subdirectory_name = "tool_crashes"

    if not report_subdirectory_name:
        return None
    log_path = result_util.get_log_path(result_output_dir)

    log_contents = util.file_read_text(log_path)
    signature = signature_util.get_signature_from_log_contents(log_contents)

    signature_dir = reports_dir / report_subdirectory_name / signature

    util.mkdirs_p(signature_dir)

    # If the signature_dir contains a NOT_INTERESTING file, then don't bother creating a report.
    if (signature_dir / "NOT_INTERESTING").exists():
        return None

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

    test_metadata = test_util.metadata_read(test_dir_in_reports)
    test_metadata.crash_signature = signature
    test_metadata.device.CopyFrom(device)
    test_util.metadata_write(test_metadata, test_dir_in_reports)

    return test_dir_in_reports


def run_reduction_on_report(test_dir: Path, reports_dir: Path) -> None:
    test = test_util.metadata_read(test_dir)

    try:
        part_1_reduced_test = run_reduction(
            test_dir_reduction_output=test_dir,
            test_dir_to_reduce=test_dir,
            preserve_semantics=True,
            reduction_name="part_1_preserve_semantics",
        )

        part_2_reduced_test = run_reduction(
            test_dir_reduction_output=test_dir,
            test_dir_to_reduce=part_1_reduced_test,
            preserve_semantics=False,
            reduction_name="part_2_change_semantics",
        )

        device_name = test.device.name

        # Create a symlink to the "best" reduction.
        best_reduced_test_link = test_util.get_reduced_test_dir(
            test_dir, device_name, fuzz.BEST_REDUCTION_NAME
        )
        util.make_directory_symlink(
            new_symlink_file_path=best_reduced_test_link,
            existing_dir=part_2_reduced_test,
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


def handle(
    test_dir: Path,
    reports_dir: Path,
    active_devices: List[Device],
    binary_manager: built_in_binaries.BinaryManager,
    settings: Settings,
) -> bool:

    report_paths: List[Path] = []
    issue_found = False

    # Run on all devices.
    for device in active_devices:
        status = run(test_dir, binary_manager, device)
        if status in (fuzz.STATUS_CRASH, fuzz.STATUS_TOOL_CRASH):
            issue_found = True
        if status == fuzz.STATUS_TOOL_CRASH:
            # No need to run further on real devices if the pre-processing step failed.
            break

    # For each device that saw a crash, copy the test to reports_dir, adding the signature and device info to the test
    # metadata.
    # TODO: Consider moving the device binaries list into test.binaries?
    for device in active_devices:
        report_dir = maybe_add_report(test_dir, reports_dir, device, settings)
        if report_dir:
            report_paths.append(report_dir)

    # For each report, run a reduction on the target device with the device-specific crash signature.
    for test_dir_in_reports in report_paths:
        run_reduction_on_report(test_dir_in_reports, reports_dir)

    # For each report, create a summary and reproduce the bug.
    for test_dir_in_reports in report_paths:
        fuzz.create_summary_and_reproduce(test_dir_in_reports, binary_manager)

    return issue_found


def run_shader_job(
    shader_job: Path,
    output_dir: Path,
    test: Test,
    device: Device,
    binary_manager: built_in_binaries.BinaryManager,
) -> Path:

    with util.file_open_text(output_dir / "log.txt", "w") as log_file:
        try:
            gflogging.push_stream_for_logging(log_file)

            binary_paths = binary_manager.get_child_binary_manager(
                list(device.binaries) + list(test.binaries)
            )

            # TODO: Find amber path. NDK or host.

            # TODO: If Amber is going to be used, check if Amber can use Vulkan debug layers now, and if not, pass that
            #  info down via a bool.

            try:

                spirv_opt_hash: Optional[str] = None
                if test.glsl.spirv_opt_args:
                    spirv_opt_hash = binary_paths.get_binary_by_name(
                        built_in_binaries.SPIRV_OPT_NAME
                    ).version

                amber_script_file = tool.glsl_shader_job_to_amber_script(
                    shader_job,
                    output_dir / "test.amber",
                    output_dir,
                    binary_paths,
                    recipe_spirv_asm_shader_job_to_amber_script.AmberfySettings(
                        spirv_opt_args=list(test.glsl.spirv_opt_args),
                        spirv_opt_hash=spirv_opt_hash,
                    ),
                    spirv_opt_args=list(test.glsl.spirv_opt_args),
                )
            except subprocess.CalledProcessError:
                util.file_write_text(
                    result_util.get_status_path(output_dir), fuzz.STATUS_TOOL_CRASH
                )
                return output_dir
            except subprocess.TimeoutExpired:
                util.file_write_text(
                    result_util.get_status_path(output_dir), fuzz.STATUS_TOOL_TIMEOUT
                )
                return output_dir

            is_compute = bool(
                shader_job_util.get_related_files(
                    shader_job, [shader_job_util.EXT_COMP]
                )
            )

            # Consider device type.

            if device.HasField("preprocess"):
                # The "preprocess" device type just needs to get this far, so this is a success.
                util.file_write_text(
                    result_util.get_status_path(output_dir), fuzz.STATUS_SUCCESS
                )
                return output_dir

            if device.HasField("host") or device.HasField("swift_shader"):
                icd: Optional[Path] = None

                if device.HasField("swift_shader"):
                    icd = binary_paths.get_binary_path_by_name(
                        built_in_binaries.SWIFT_SHADER_NAME
                    ).path

                # Run the shader on the host using Amber.
                host_device_util.run_amber(
                    amber_script_file,
                    output_dir,
                    dump_image=(not is_compute),
                    dump_buffer=is_compute,
                    icd=icd,
                )
                return output_dir

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


def run_reduction(
    test_dir_reduction_output: Path,
    test_dir_to_reduce: Path,
    preserve_semantics: bool,
    reduction_name: str = "reduction1",
    device_name: Optional[str] = None,
) -> Path:
    test = test_util.metadata_read(test_dir_to_reduce)

    if not device_name and not test.device:
        raise AssertionError(
            f"Cannot reduce {str(test_dir_to_reduce)}; device must be specified in {str(test_util.get_metadata_path(test_dir_to_reduce))}"
        )

    if not device_name:
        device_name = test.device.name

    if not test.crash_signature:
        raise AssertionError(
            f"Cannot reduce {str(test_dir_to_reduce)} because there is no crash string specified; "
            f"for now, only crash reductions are supported"
        )

    reduced_test_dir_1 = test_util.get_reduced_test_dir(
        test_dir_reduction_output, device_name, reduction_name
    )

    reduction_work_variant_dir = run_glsl_reduce(
        input_shader_job=test_util.get_shader_job_path(
            test_dir_to_reduce, is_variant=True
        ),
        test_metadata_path=test_util.get_metadata_path(test_dir_to_reduce),
        output_dir=test_util.get_reduction_work_directory(
            reduced_test_dir_1, is_variant=True
        ),
        preserve_semantics=preserve_semantics,
    )

    final_reduced_shader_job_path = get_final_reduced_shader_job_path(
        reduction_work_variant_dir
    )

    check(
        final_reduced_shader_job_path.exists(),
        ReductionFailedError(
            "Reduction failed; not yet handled",
            reduction_name,
            reduction_work_variant_dir,
        ),
    )

    # Finally, write the test metadata and shader job, so the returned directory can be used as a test_dir.

    test_util.metadata_write(test, reduced_test_dir_1)

    shader_job_util.copy(
        final_reduced_shader_job_path,
        test_util.get_shader_job_path(reduced_test_dir_1, is_variant=True),
    )

    return reduced_test_dir_1


def run_glsl_reduce(
    input_shader_job: Path,
    test_metadata_path: Path,
    output_dir: Path,
    preserve_semantics: bool = False,
) -> Path:

    cmd = [
        "glsl-reduce",
        str(input_shader_job),
        "--output",
        str(output_dir),
        "--",
        "gfauto_interestingness_test",
        str(test_metadata_path),
    ]

    if preserve_semantics:
        cmd.insert(1, "--preserve-semantics")

    # Log the reduction.
    with util.file_open_text(output_dir / "command.log", "w") as f:
        gflogging.push_stream_for_logging(f)
        try:
            # The reducer can fail, but it will typically output an exception file, so we can ignore the exit code.
            subprocess_util.run(cmd, verbose=True, check_exit_code=False)
        finally:
            gflogging.pop_stream_for_logging()

    return output_dir


def create_summary_and_reproduce_glsl(
    test_dir: Path,
    binary_manager: built_in_binaries.BinaryManager,
    device: Optional[Device] = None,
) -> None:
    test_metadata = test_util.metadata_read(test_dir)
    if not device:
        device = test_metadata.device

    summary_dir = test_dir / "summary"

    unreduced_glsl = util.copy_dir(
        test_util.get_source_dir(test_dir), summary_dir / "unreduced_glsl"
    )

    reduced_test_dir = test_util.get_reduced_test_dir(
        test_dir, test_metadata.device.name, fuzz.BEST_REDUCTION_NAME
    )
    reduced_source_dir = test_util.get_source_dir(reduced_test_dir)
    reduced_glsl = util.copy_dir(reduced_source_dir, summary_dir / "reduced_glsl")

    run_shader_job(
        unreduced_glsl / test_util.VARIANT_DIR / test_util.SHADER_JOB,
        summary_dir / "unreduced_glsl_result" / test_util.VARIANT_DIR,
        test_metadata,
        device,
        binary_manager,
    )

    variant_reduced_glsl_result = run_shader_job(
        reduced_glsl / test_util.VARIANT_DIR / test_util.SHADER_JOB,
        summary_dir / "reduced_glsl_result" / test_util.VARIANT_DIR,
        test_metadata,
        device,
        binary_manager,
    )

    # Some post-processing for common error types.

    status = result_util.get_status(variant_reduced_glsl_result)
    if status == fuzz.STATUS_TOOL_CRASH:
        tool_crash_summary_bug_report_dir(
            reduced_source_dir,
            variant_reduced_glsl_result,
            summary_dir,
            binary_manager,
            test_metadata,
        )


def tool_crash_summary_bug_report_dir(  # pylint: disable=too-many-locals;
    reduced_glsl_source_dir: Path,
    variant_reduced_glsl_result_dir: Path,
    summary_dir: Path,
    binary_manager: built_in_binaries.BinaryManager,
    test_metadata: Test,
) -> Path:
    # Create a simple script and README.

    shader_job = reduced_glsl_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB

    shader_files = shader_job_util.get_related_files(
        shader_job, shader_job_util.EXT_ALL
    )
    check(
        len(shader_files) > 0,
        AssertionError(f"Need at least one shader for {shader_job}"),
    )

    shader_extension = shader_files[0].suffix

    bug_report_dir = util.copy_dir(
        variant_reduced_glsl_result_dir, summary_dir / "bug_report"
    )

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
    readme += f"* glslangValidator commit hash: {binary_manager.get_binary_by_name(built_in_binaries.GLSLANG_VALIDATOR_NAME).version}\n"

    if test_metadata.glsl.spirv_opt_args:
        readme += f"* spirv-opt commit hash: {binary_manager.get_binary_by_name(built_in_binaries.SPIRV_OPT_NAME).version}\n"

    readme += "\nTo reproduce:\n\n"
    readme += f"`glslangValidator -V shader{shader_extension} -o shader{shader_extension}.spv`\n\n"

    if spv_files and not test_metadata.glsl.spirv_opt_args:
        # There was an .spv file and no spirv-opt, so validate the SPIR-V.
        readme += f"`spirv-val shader{shader_extension}.spv`\n\n"

    if test_metadata.glsl.spirv_opt_args:
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

    util.file_write_text(summary_dir / "README.md", readme)

    return bug_report_dir
