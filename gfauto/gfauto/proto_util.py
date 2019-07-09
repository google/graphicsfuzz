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

from pathlib import Path
from typing import TypeVar

from google.protobuf import json_format
from google.protobuf.message import Message

from gfauto import util

# pylint: disable=invalid-name; Generic type variable names are usually one letter.
M = TypeVar("M", bound=Message)  # noqa


def json_to_message(json: str, message: M) -> M:
    json_format.Parse(json, message, ignore_unknown_fields=True)
    return message


def message_to_json(
    message: Message, including_default_value_fields: bool = True
) -> str:
    return json_format.MessageToJson(
        message,
        including_default_value_fields=including_default_value_fields,
        preserving_proto_field_name=True,
        sort_keys=True,
    )


def message_to_file(
    message: Message,
    output_file_path: Path,
    including_default_value_fields: bool = True,
) -> Path:
    message_json = message_to_json(message, including_default_value_fields)
    util.file_write_text(output_file_path, message_json)
    return output_file_path


def file_to_message(input_file_path: Path, message: M) -> M:
    message_json = util.file_read_text(input_file_path)
    return json_to_message(message_json, message)
