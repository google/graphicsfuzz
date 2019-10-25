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

from pathlib import Path

from gfauto import devices_util, proto_util
from gfauto.settings_pb2 import Settings

DEFAULT_SETTINGS_FILE_PATH = Path("settings.json")

DEFAULT_SETTINGS = Settings(
    maximum_duplicate_crashes=3,
    maximum_fuzz_failures=10,
    reduce_tool_crashes=True,
    reduce_crashes=True,
    reduce_bad_images=True,
)


class NoSettingsFile(Exception):
    pass


def read_or_create(settings_path: Path) -> Settings:
    try:
        return read(settings_path)
    except FileNotFoundError as exception:
        message = f'gfauto could not find "{settings_path}"'
        if not DEFAULT_SETTINGS_FILE_PATH.exists():
            write_default(DEFAULT_SETTINGS_FILE_PATH)
            message += (
                f'; a default settings file "{str(DEFAULT_SETTINGS_FILE_PATH)}" has been created for you. '
                f"Please review it and then try again. \n"
            )
        raise NoSettingsFile(message) from exception


def read(settings_path: Path) -> Settings:
    result = proto_util.file_to_message(settings_path, Settings())
    return result


def write(settings: Settings, settings_path: Path) -> Path:
    return proto_util.message_to_file(settings, settings_path)


def write_default(settings_path: Path) -> Path:
    settings = Settings()
    settings.CopyFrom(DEFAULT_SETTINGS)
    devices_util.get_device_list(settings.device_list)
    return write(settings, settings_path)
