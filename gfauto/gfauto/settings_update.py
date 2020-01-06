# -*- coding: utf-8 -*-

# Copyright 2020 The GraphicsFuzz Project Authors
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

"""Updates (or creates) a settings.json file."""

import argparse
import sys
from pathlib import Path

from gfauto import binaries_util, devices_util, settings_util
from gfauto.gflogging import log


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Updates or creates a settings.json file. "
        "If the settings file does not exist, a default settings file will be created and the tool will exit. "
        "If the settings file exists, it is read, updated, and written; command line arguments define the update steps. "
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "--update_binary_versions",
        help="Update binary versions to the latest available (online).",
        action="store_true",
    )

    parser.add_argument(
        "--update_devices",
        help='Update each device in the settings.json file. This mostly just means updating the device_properties field obtained by running "amber -d -V". '
        "SwiftShader devices will be updated to use the latest version of SwiftShader and Android devices will update their build_fingerprint field. "
        "It is recommended that you also pass --update_binary_versions so that the binary versions are updated first. ",
        action="store_true",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    # Args.
    settings_path: Path = Path(parsed_args.settings)
    update_binary_versions: bool = parsed_args.update_binary_versions
    update_devices: bool = parsed_args.update_devices

    settings = settings_util.read_or_create(settings_path)

    if update_binary_versions:
        del settings.latest_binary_versions[:]
        settings.latest_binary_versions.extend(
            binaries_util.download_latest_binary_version_numbers()
        )

    if update_devices:
        binary_manager = binaries_util.get_default_binary_manager(settings)
        for device in settings.device_list.devices:
            devices_util.update_device(binary_manager, device)

    settings_util.write(settings, settings_path)


if __name__ == "__main__":
    try:
        main()
    except settings_util.NoSettingsFile as exception:
        log(str(exception))
