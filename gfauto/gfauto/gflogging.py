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
from typing import List, TextIO

from .util import file_read_text

_LOG_TO_STDOUT = True
_LOG_TO_STREAM: List[TextIO] = []


def push_stream_for_logging(stream: TextIO) -> None:
    _LOG_TO_STREAM.append(stream)


def pop_stream_for_logging() -> None:
    _LOG_TO_STREAM.pop()


def log(message: str) -> None:
    if _LOG_TO_STDOUT:
        print(message, flush=True)  # noqa T001
    for stream in _LOG_TO_STREAM:
        stream.write(message)
        stream.write("\n")
        stream.flush()


def log_a_file(log_file: Path) -> None:
    log(f"Logging the contents of {str(log_file)}")
    try:
        log(file_read_text(log_file))
    except IOError:
        log(f"Failed to read {str(log_file)}")
