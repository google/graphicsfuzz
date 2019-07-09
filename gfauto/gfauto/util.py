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

import os
import pathlib
import platform
import shutil
from contextlib import contextmanager
from pathlib import Path
from typing import Any, BinaryIO, Iterator, List, TextIO, cast

MIN_SIGNED_INT_32 = -pow(2, 31)
MAX_SIGNED_INT_32 = pow(2, 31) - 1

# Note: Could use the built-in |file.open| and |file.write_text|, etc.


def file_open_binary(file: pathlib.Path, mode: str) -> BinaryIO:  # noqa VNE002
    check("b" in mode, AssertionError(f"|mode|(=={mode}) should contain 'b'"))
    # Type hint (no runtime check).
    result = cast(BinaryIO, open(str(file), mode))
    return result


def file_open_text(file: pathlib.Path, mode: str) -> TextIO:  # noqa VNE002
    check("b" not in mode, AssertionError(f"|mode|(=={mode}) should not contain 'b'"))
    if "w" in mode:
        file_mkdirs_parent(file)
    # Type hint (no runtime check).
    result = cast(TextIO, open(str(file), mode, encoding="utf-8", errors="ignore"))
    return result


def file_read_text_or_else(text_file: pathlib.Path, or_else: str) -> str:
    try:
        return file_read_text(text_file)
    except IOError:
        return or_else


def file_read_text(file: pathlib.Path) -> str:  # noqa VNE002
    with file_open_text(file, "r") as f:
        return f.read()


def file_read_lines(file: pathlib.Path) -> List[str]:  # noqa VNE002
    with file_open_text(file, "r") as f:
        return f.readlines()


def file_write_text(file: pathlib.Path, text: str) -> int:  # noqa VNE002
    file_mkdirs_parent(file)
    with file_open_text(file, "w") as f:
        return f.write(text)


def mkdirs_p(path: pathlib.Path) -> Path:  # noqa VNE002
    path.mkdir(parents=True, exist_ok=True)
    return path


def file_mkdirs_parent(file: pathlib.Path) -> None:  # noqa VNE002
    mkdirs_p(file.parent)


class ToolNotOnPathError(Exception):
    pass


def tool_on_path(tool: str) -> pathlib.Path:  # noqa VNE002
    result = shutil.which(tool)
    if result is None:
        raise ToolNotOnPathError(
            "Could not find {} on PATH. Please add to PATH.".format(tool)
        )
    return pathlib.Path(result)


def prepend_catchsegv_if_available(cmd: List[str]) -> List[str]:
    try:
        cmd.insert(0, str(tool_on_path("catchsegv")))
    except ToolNotOnPathError:
        pass

    return cmd


def copy_file(
    source_file_path: pathlib.Path, dest_file_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_file_path)
    shutil.copy(str(source_file_path), str(dest_file_path))
    return dest_file_path


def copy_dir(
    source_dir_path: pathlib.Path, dest_dir_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_dir_path)
    shutil.copytree(source_dir_path, dest_dir_path)
    return dest_dir_path


def move_dir(
    source_dir_path: pathlib.Path, dest_dir_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_dir_path)
    shutil.move(source_dir_path, dest_dir_path)
    return dest_dir_path


def make_directory_symlink(new_symlink_file_path: Path, existing_dir: Path) -> Path:
    check(existing_dir.is_dir(), AssertionError(f"Not a directory: {existing_dir}"))
    file_mkdirs_parent(new_symlink_file_path)
    symlink_contents = os.path.relpath(
        str(existing_dir), start=str(new_symlink_file_path.parent)
    )
    new_symlink_file_path.symlink_to(symlink_contents, target_is_directory=True)
    return new_symlink_file_path


def remove_start(string: str, start: str) -> str:
    check(
        string.startswith(start), AssertionError("|string| does not start with |start|")
    )

    return string[len(start) :]


def remove_end(str_in: str, str_end: str) -> str:
    check(
        str_in.endswith(str_end),
        AssertionError(f"|str_in|(=={str_in}) should end with |str_end|(=={str_end})"),
    )
    return str_in[: -len(str_end)]


def norm_path(path: pathlib.Path) -> pathlib.Path:  # noqa VNE002
    return pathlib.Path(os.path.normpath(str(path)))


@contextmanager
def pushd(path: pathlib.Path) -> Iterator[None]:  # noqa VNE002
    current_dir = pathlib.Path().resolve()
    os.chdir(str(path))
    try:
        yield
    finally:
        os.chdir(str(current_dir))


def check(condition: bool, exception: Exception) -> None:
    if not condition:
        raise exception


def check_field_truthy(field: Any, field_name: str) -> None:
    if not field:
        raise ValueError(f"{field_name}(={str(field)}) must be filled")


def check_file_exists(path: Path) -> None:
    check(path.exists(), FileNotFoundError(f"Could not find {str(path)}"))


def get_platform() -> str:
    host = platform.system()
    if host in ("Linux", "Windows"):
        return host
    if host == "Darwin":
        return "Mac"
    raise AssertionError("Unsupported platform: {}".format(host))
