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

"""Runs GraphicsFuzz AmberScript tests."""

import argparse
import subprocess
import sys
from pathlib import Path
from typing import List, Optional, Set, TextIO

from gfauto import (
    android_device,
    binaries_util,
    devices_util,
    fuzz,
    gflogging,
    host_device_util,
    result_util,
    settings_util,
    shader_compiler_util,
    signature_util,
    spirv_opt_util,
    util,
)
from gfauto.device_pb2 import Device
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings

DEFAULT_TIMEOUT = 30
SPIRV_OPT_O = "spirv-opt -O"
SPIRV_OPT_OS = "spirv-opt -Os"
SPIRV_OPT_CUSTOM = "spirv-opt -Oconfig=custom"


def main() -> None:  # pylint: disable=too-many-locals,too-many-branches,too-many-statements;
    parser = argparse.ArgumentParser(
        description="Runs GraphicsFuzz AmberScript tests on the active devices listed in "
        "the settings.json file.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "--tests",
        help="Path to the directory of AmberScript tests with shaders extracted.",
        default="graphicsfuzz",
    )

    parser.add_argument(
        "--update_ignored_crash_signatures",
        help="As the tests are run for each device, add any crash signatures to the device's ignored_crash_signatures "
        "property and write out the updated settings.json file.",
        action="store_true",
    )

    parser.add_argument(
        "--results_out",
        help="Output file path for the CSV results table.",
        default="results.csv",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    # Args.
    tests_dir: Path = Path(parsed_args.tests)
    settings_path: Path = Path(parsed_args.settings)
    update_ignored_crash_signatures: bool = parsed_args.update_ignored_crash_signatures
    results_out_path: Path = Path(parsed_args.results_out)

    # Settings and devices.
    settings = settings_util.read_or_create(settings_path)
    active_devices = devices_util.get_active_devices(settings.device_list)

    # Binaries.
    binaries = binaries_util.get_default_binary_manager(settings=settings)

    work_dir = Path() / "temp" / f"graphicsfuzz_cts_run_{fuzz.get_random_name()[:8]}"
    with util.file_open_text(results_out_path, "w") as results_out_handle:
        main_helper(
            tests_dir=tests_dir,
            work_dir=work_dir,
            binaries=binaries,
            settings=settings,
            active_devices=active_devices,
            results_out_handle=results_out_handle,
            updated_settings_output_path=(
                settings_path if update_ignored_crash_signatures else None
            ),
        )


def main_helper(  # pylint: disable=too-many-locals,too-many-branches,too-many-statements;
    tests_dir: Path,
    work_dir: Path,
    binaries: binaries_util.BinaryManager,
    settings: Settings,
    active_devices: List[Device],
    results_out_handle: Optional[TextIO],
    updated_settings_output_path: Optional[Path],
) -> None:

    util.mkdirs_p(work_dir)

    def write_entry(entry: str) -> None:
        if not results_out_handle:
            return
        results_out_handle.write(entry)
        results_out_handle.write(", ")
        results_out_handle.flush()

    def write_newline() -> None:
        if not results_out_handle:
            return
        results_out_handle.write("\n")
        results_out_handle.flush()

    spirv_opt_path: Optional[Path] = None
    swift_shader_path: Optional[Path] = None
    amber_path: Optional[Path] = None

    # Small hack to ensure we have three devices for spirv-opt, each with a different name.
    main_spirv_opt_device: Optional[Device] = None

    if active_devices and active_devices[0].name == "host_preprocessor":
        main_spirv_opt_device = active_devices[0]
        main_spirv_opt_device.name = SPIRV_OPT_O

        spirv_opt_custom = Device()
        spirv_opt_custom.CopyFrom(main_spirv_opt_device)
        spirv_opt_custom.name = SPIRV_OPT_CUSTOM
        active_devices.insert(1, spirv_opt_custom)

        spirv_opt_os = Device()
        spirv_opt_os.CopyFrom(main_spirv_opt_device)
        spirv_opt_os.name = SPIRV_OPT_OS
        active_devices.insert(1, spirv_opt_os)

    # Enumerate active devices, writing their name and storing binary paths if needed.
    write_entry("test")
    for device in active_devices:
        write_entry(device.name)

        if device.HasField("preprocess"):
            spirv_opt_path = binaries.get_binary_path_by_name(
                binaries_util.SPIRV_OPT_NAME
            ).path

        if device.HasField("swift_shader"):
            swift_shader_path = binaries.get_binary_path_by_name(
                binaries_util.SWIFT_SHADER_NAME
            ).path

        if device.HasField("swift_shader") or device.HasField("host"):
            amber_path = binaries.get_binary_path_by_name(binaries_util.AMBER_NAME).path

    write_newline()

    # Enumerate tests and devices, writing the results.

    for test in sorted(tests_dir.glob("*.amber")):
        test_name = util.remove_end(test.name, ".amber")
        write_entry(test_name)
        spirv_shaders = sorted(
            tests_dir.glob(util.remove_end(test.name, "amber") + "*.spv")
        )
        for device in active_devices:
            test_run_dir = work_dir / f"{test_name}_{device.name}"
            util.mkdirs_p(test_run_dir)
            ignored_signatures_set: Set[str] = set(device.ignored_crash_signatures)

            with util.file_open_text(test_run_dir / "log.txt", "w") as log_stream:
                try:
                    gflogging.push_stream_for_logging(log_stream)
                    if device.HasField("preprocess"):
                        # This just means spirv-opt for now.

                        assert spirv_opt_path  # noqa
                        assert main_spirv_opt_device  # noqa

                        # Pick spirv-opt arguments based on device name.
                        if device.name == SPIRV_OPT_O:
                            spirv_opt_args = ["-O"]
                        elif device.name == SPIRV_OPT_OS:
                            spirv_opt_args = ["-Os"]
                        elif device.name == SPIRV_OPT_CUSTOM:
                            spirv_opt_args = (
                                spirv_opt_util.OPT_INTERESTING_SUBSET_OF_PASSES
                            )
                        else:
                            raise AssertionError(
                                f"Can't tell how to run device {device.name}; "
                                f"must be named host_preprocessor and be the first active device."
                            )

                        # Reset device and ignored_crash_signatures.
                        device = main_spirv_opt_device
                        ignored_signatures_set = set(device.ignored_crash_signatures)

                        try:
                            for spirv_shader in spirv_shaders:
                                spirv_opt_util.run_spirv_opt_on_spirv_shader(
                                    spirv_shader,
                                    test_run_dir,
                                    spirv_opt_args,
                                    spirv_opt_path,
                                )
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_SUCCESS,
                            )
                        except subprocess.CalledProcessError:
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_TOOL_CRASH,
                            )
                        except subprocess.TimeoutExpired:
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_TOOL_TIMEOUT,
                            )
                    elif device.HasField("shader_compiler"):
                        try:
                            for spirv_shader in spirv_shaders:
                                shader_compiler_util.run_shader(
                                    shader_compiler_device=device.shader_compiler,
                                    shader_path=spirv_shader,
                                    output_dir=test_run_dir,
                                    compiler_path=binaries.get_binary_path_by_name(
                                        device.shader_compiler.binary
                                    ).path,
                                    timeout=DEFAULT_TIMEOUT,
                                )
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_SUCCESS,
                            )
                        except subprocess.CalledProcessError:
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_CRASH,
                            )
                        except subprocess.TimeoutExpired:
                            result_util.write_status(
                                test_run_dir, fuzz.STATUS_TIMEOUT,
                            )
                    elif device.HasField("swift_shader"):
                        assert swift_shader_path  # noqa
                        assert amber_path  # noqa
                        host_device_util.run_amber(
                            test,
                            test_run_dir,
                            amber_path=amber_path,
                            dump_image=False,
                            dump_buffer=False,
                            icd=swift_shader_path,
                        )
                    elif device.HasField("host"):
                        assert amber_path  # noqa
                        host_device_util.run_amber(
                            test,
                            test_run_dir,
                            amber_path=amber_path,
                            dump_image=False,
                            dump_buffer=False,
                            custom_launcher=list(device.host.custom_launcher),
                        )
                    elif device.HasField("android"):
                        android_device.run_amber_on_device(
                            test,
                            test_run_dir,
                            dump_image=False,
                            dump_buffer=False,
                            serial=device.android.serial,
                        )
                    else:
                        raise AssertionError(f"Unsupported device {device.name}")

                finally:
                    gflogging.pop_stream_for_logging()

            status = result_util.get_status(test_run_dir)

            if status == fuzz.STATUS_SUCCESS:
                write_entry("P")
            elif status in (fuzz.STATUS_TIMEOUT, fuzz.STATUS_TOOL_TIMEOUT):
                write_entry("T")
            else:
                write_entry("F")

            # Update ignored signatures.
            if (
                status
                in (
                    fuzz.STATUS_TOOL_CRASH,
                    fuzz.STATUS_CRASH,
                    fuzz.STATUS_UNRESPONSIVE,
                )
                and updated_settings_output_path
            ):
                log_contents = util.file_read_text(
                    result_util.get_log_path(test_run_dir)
                )
                signature = signature_util.get_signature_from_log_contents(log_contents)
                if signature == signature_util.NO_SIGNATURE:
                    log(f"NOT updating ignored signatures to include {signature}")
                elif signature in ignored_signatures_set:
                    log(f"Signature is already ignored: {signature}")
                else:
                    log(f"Adding ignored signature: {signature}")
                    device.ignored_crash_signatures.append(signature)

        write_newline()

    if updated_settings_output_path:
        # Reset main_spirv_opt_device name before writing it back out.
        if main_spirv_opt_device:
            main_spirv_opt_device.name = "host_preprocessor"
        settings_util.write(settings, updated_settings_output_path)


if __name__ == "__main__":
    main()
