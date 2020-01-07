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

"""Settings utility module.

Used to read and write the Settings proto.
See settings.proto.
"""
import traceback
from pathlib import Path

from gfauto import binaries_util, devices_util, proto_util
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings

DEFAULT_SETTINGS_FILE_PATH = Path("settings.json")

DEFAULT_SETTINGS = Settings(
    maximum_duplicate_crashes=3,
    maximum_fuzz_failures=10,
    reduce_tool_crashes=True,
    reduce_crashes=True,
    reduce_bad_images=True,
    _comment="https://github.com/google/graphicsfuzz/blob/master/gfauto/gfauto/settings.proto",
)


class NoSettingsFile(Exception):
    pass


def read_or_create(settings_path: Path) -> Settings:
    try:
        return read(settings_path)
    except FileNotFoundError as exception:
        if settings_path.exists():
            raise
        log(
            f'\ngfauto could not find "{settings_path}" so one will be created for you\n'
        )
        write_default(settings_path)
        raise NoSettingsFile(
            f'\ngfauto could not find "{settings_path}" so one was created for you. Please review "{settings_path}" and try again.\n'
        ) from exception


def read(settings_path: Path) -> Settings:
    result = proto_util.file_to_message(settings_path, Settings())
    return result


def write(settings: Settings, settings_path: Path) -> Path:
    return proto_util.message_to_file(settings, settings_path)


def write_default(settings_path: Path) -> Path:
    settings = Settings()
    settings.CopyFrom(DEFAULT_SETTINGS)

    # noinspection PyBroadException
    try:

        settings.latest_binary_versions.extend(
            binaries_util.download_latest_binary_version_numbers()
        )
    except Exception:  # pylint: disable=broad-except;
        message = "WARNING: Could not download the latest binary version numbers. We will just use the (older) hardcoded versions."
        details = traceback.format_exc()  # noqa: SC100, SC200 (spelling of exc)

        log(message)
        log(f"\nDetails:\n{details}\n\n")
        log(message)

        settings.latest_binary_versions.extend(binaries_util.DEFAULT_BINARIES)

    binary_manager = binaries_util.get_default_binary_manager(settings=settings)

    devices_util.get_device_list(binary_manager, settings.device_list)

    return write(settings, settings_path)
