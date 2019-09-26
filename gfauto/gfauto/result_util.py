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

"""Result utility module.

When we run Amber, we write certain files to indicate the result.
This module contains functions for getting the paths of these files.
"""

from pathlib import Path
from typing import Optional

from gfauto import util


def write_status(
    result_output_dir: Path, status: str, bad_shader_name: Optional[str] = None
) -> None:
    util.file_write_text(get_status_path(result_output_dir), status)
    if bad_shader_name:
        util.file_write_text(
            get_status_bad_shader_name_path(result_output_dir), bad_shader_name
        )


def get_status_path(result_output_dir: Path) -> Path:
    return result_output_dir / "STATUS"


def get_status_bad_shader_name(result_output_dir: Path) -> str:
    bad_shader_name_path = get_status_bad_shader_name_path(result_output_dir)
    return util.file_read_text_or_else(bad_shader_name_path, "")


def get_status_bad_shader_name_path(result_output_dir: Path) -> Path:
    return result_output_dir / "BAD_SHADER"


def get_status(result_output_dir: Path) -> str:
    status_file = get_status_path(result_output_dir)
    return util.file_read_text_or_else(status_file, "UNEXPECTED_ERROR")


def get_log_path(result_output_dir: Path) -> Path:
    return result_output_dir / "log.txt"


def get_amber_log_path(result_dir: Path) -> Path:
    return result_dir / "amber_log.txt"
