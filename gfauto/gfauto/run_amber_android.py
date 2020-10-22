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

"""Runs AmberScript tests on Android devices."""

import argparse
import sys
from pathlib import Path
from typing import List, Optional

from gfauto import android_device, binaries_util, settings_util
from gfauto.settings_pb2 import Settings


def main() -> None:  # pylint: disable=too-many-statements, too-many-locals, too-many-branches;
    parser = argparse.ArgumentParser(
        description="Runs AmberScript files on Android devices."
    )

    parser.add_argument(
        "amber_script_file", help="AmberScript tests to run.", nargs="+",
    )

    parser.add_argument(
        "--output", help="Output directory.", default="output",
    )

    parser.add_argument(
        "--settings",
        help="Path to a settings JSON file for this instance. "
        "Unlike with gfauto_fuzz, the default value is an empty string, which is ignored. ",
        default="",
    )

    parser.add_argument(
        "--serial",
        help="Android device serial. If left unspecified, the tests will be run on all Android devices.",
        action="append",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    amber_script_files: List[Path] = [Path(a) for a in parsed_args.amber_script_file]
    output_path: Path = Path(parsed_args.output)
    serials: Optional[List[str]] = parsed_args.serial
    settings_str: str = parsed_args.settings

    settings = Settings()
    if settings_str:
        settings = settings_util.read_or_create(Path(settings_str))

    binary_manager = binaries_util.get_default_binary_manager(settings)
    if not serials:
        android_devices = android_device.get_all_android_devices(
            binary_manager, include_device_details=False
        )
        serials = []
        for device in android_devices:
            serials.append(device.android.serial)

    for amber_script_file in amber_script_files:
        for serial in serials:
            android_device.run_amber_on_device(
                amber_script_file,
                output_path / serial,
                dump_image=False,
                dump_buffer=False,
                serial=serial,
            )


if __name__ == "__main__":
    main()
