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

from gfauto import util


def get_status_path(result_output_dir: Path) -> Path:
    return result_output_dir / "STATUS"


def get_status(result_output_dir: Path) -> str:
    status_file = get_status_path(result_output_dir)
    return util.file_read_text_or_else(status_file, "UNEXPECTED_ERROR")


def get_log_path(result_output_dir: Path) -> Path:
    return result_output_dir / "log.txt"


def get_amber_log_path(result_dir: Path) -> Path:
    return result_dir / "amber_log.txt"
