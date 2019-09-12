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
    artifact_util,
    binaries_util,
    devices_util,
    fuzz,
    host_device_util,
    settings_util,
    shader_compiler_util,
    spirv_opt_util,
    util,
)

DEFAULT_TIMEOUT = 30


def main() -> None:  # pylint: disable=too-many-locals,too-many-branches;
    parser = argparse.ArgumentParser(
        description="Runs GraphicsFuzz AmberScript tests on the active devices listed in "
        "the settings.json file."
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this fuzzing instance.",
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
    artifact_util.recipes_write_built_in()
    binaries = binaries_util.BinaryManager()

    work_dir = Path() / "temp" / f"cts_run_{fuzz.get_random_name()[:8]}"

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

        # Enumerate active devices, writing their name and storing binary paths if needed.
        write_entry("test")
        for device in active_devices:
            if device.HasField("preprocess"):
                write_entry("spirv-opt")
                spirv_opt_path = binaries.get_binary_path_by_name(
                    binaries_util.SPIRV_OPT_NAME
                ).path
            elif device.HasField("swift_shader"):
                write_entry("SwiftShader")
                swift_shader_path = binaries.get_binary_path_by_name(
                    binaries_util.SWIFT_SHADER_NAME
                ).path
            else:
                write_entry(device.name)

        write_newline()

        # Enumerate tests and devices, writing the results.

        for test in sorted(tests_dir.glob("*.amber")):
            write_entry(test.name)
            spirv_shaders = sorted(
                tests_dir.glob(util.remove_end(test.name, "amber") + "*.spv")
            )
            for device in active_devices:
                try:
                    if device.HasField("preprocess"):
                        # This just means spirv-op for now.

                        assert spirv_opt_path  # noqa
                        for spirv_shader in spirv_shaders:
                            spirv_opt_util.run_spirv_opt_on_spirv_shader(
                                spirv_shader, work_dir, ["-O"], spirv_opt_path
                            )
                    elif device.HasField("shader_compiler"):
                        for spirv_shader in spirv_shaders:
                            shader_compiler_util.run_shader(
                                shader_compiler_device=device.shader_compiler,
                                shader_path=spirv_shader,
                                output_dir=work_dir,
                                timeout=DEFAULT_TIMEOUT,
                            )
                    elif device.HasField("swift_shader"):
                        assert swift_shader_path  # noqa
                        host_device_util.run_amber(
                            test,
                            work_dir,
                            dump_image=False,
                            dump_buffer=False,
                            icd=swift_shader_path,
                        )
                    else:
                        raise AssertionError(f"Unsupported device {device.name}")

                    write_entry("P")
                except CalledProcessError:
                    write_entry("F")
                except TimeoutExpired:
                    write_entry("T")
            write_newline()


if __name__ == "__main__":
    main()
