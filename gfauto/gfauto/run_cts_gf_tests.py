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
import sys
from pathlib import Path
from subprocess import CalledProcessError, TimeoutExpired
from typing import Optional

from gfauto import (
    android_device,
    binaries_util,
    devices_util,
    fuzz,
    host_device_util,
    result_util,
    settings_util,
    shader_compiler_util,
    spirv_opt_util,
    util,
)

DEFAULT_TIMEOUT = 30


def main() -> None:  # pylint: disable=too-many-locals,too-many-branches,too-many-statements;
    parser = argparse.ArgumentParser(
        description="Runs GraphicsFuzz AmberScript tests on the active devices listed in "
        "the settings.json file."
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "--tests",
        help="Path to the directory of AmberScript tests with shaders extracted.",
        default=str("graphicsfuzz"),
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    # Args.
    tests_dir: Path = Path(parsed_args.tests)
    settings_path: Path = Path(parsed_args.settings)

    # Settings and devices.
    settings = settings_util.read_or_create(settings_path)
    active_devices = devices_util.get_active_devices(settings.device_list)

    # Binaries.
    binaries = binaries_util.get_default_binary_manager(settings=settings)

    work_dir = Path() / "temp" / f"cts_run_{fuzz.get_random_name()[:8]}"

    util.mkdirs_p(work_dir)

    with util.file_open_text(Path("results.txt"), "w") as log_handle:

        def write_entry(entry: str) -> None:
            log_handle.write(entry)
            log_handle.write(", ")
            log_handle.flush()

        def write_newline() -> None:
            log_handle.write("\n")
            log_handle.flush()

        spirv_opt_path: Optional[Path] = None
        swift_shader_path: Optional[Path] = None
        amber_path: Optional[Path] = None

        # Enumerate active devices, writing their name and storing binary paths if needed.
        write_entry("test")
        for device in active_devices:

            if device.name == "host_preprocessor":
                # We are actually just running spirv-opt on the SPIR-V shaders.
                write_entry("spirv-opt")
            else:
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
                amber_path = binaries.get_binary_path_by_name(
                    binaries_util.AMBER_NAME
                ).path

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
                try:
                    # Confusingly, some functions below will raise on an error; others will write e.g. CRASH to the
                    # STATUS file in the output directory. In the latter case, we update |status|. We check |status| at
                    # the end of this if-else chain and raise fake exceptions if appropriate.
                    status = fuzz.STATUS_SUCCESS

                    if device.HasField("preprocess"):
                        # This just means spirv-op for now.

                        assert spirv_opt_path  # noqa
                        for spirv_shader in spirv_shaders:
                            spirv_opt_util.run_spirv_opt_on_spirv_shader(
                                spirv_shader, test_run_dir, ["-O"], spirv_opt_path
                            )
                    elif device.HasField("shader_compiler"):
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
                        status = result_util.get_status(test_run_dir)
                    elif device.HasField("host"):
                        assert amber_path  # noqa
                        host_device_util.run_amber(
                            test,
                            test_run_dir,
                            amber_path=amber_path,
                            dump_image=False,
                            dump_buffer=False,
                        )
                        status = result_util.get_status(test_run_dir)
                    elif device.HasField("android"):
                        android_device.run_amber_on_device(
                            test,
                            test_run_dir,
                            dump_image=False,
                            dump_buffer=False,
                            serial=device.android.serial,
                        )
                        status = result_util.get_status(test_run_dir)
                    else:
                        raise AssertionError(f"Unsupported device {device.name}")

                    if status in (fuzz.STATUS_CRASH, fuzz.STATUS_TOOL_CRASH):
                        raise CalledProcessError(1, "??")
                    if status != fuzz.STATUS_SUCCESS:
                        raise TimeoutExpired("??", fuzz.AMBER_RUN_TIME_LIMIT)

                    write_entry("P")
                except CalledProcessError:
                    write_entry("F")
                except TimeoutExpired:
                    write_entry("T")
            write_newline()


if __name__ == "__main__":
    main()
