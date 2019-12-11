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

"""General utility module.

Used for accessing files, file system operations like creating directories, copying, moving, etc., getting the full path
of a tool on the PATH, removing the beginning and/or end of string, and custom assert functions like check,
check_check_field_truthy, check_file_exists, etc.
"""
import hashlib
import os
import pathlib
import platform
import shutil
import uuid
import zipfile
from contextlib import contextmanager
from pathlib import Path
from typing import Any, BinaryIO, Dict, Iterator, List, Optional, TextIO, Tuple, cast

import attr

from gfauto import gflogging

MIN_SIGNED_INT_32 = -pow(2, 31)
MAX_SIGNED_INT_32 = pow(2, 31) - 1

# Note: Could use the built-in |file.open| and |file.write_text|, etc.


def file_open_binary(file: pathlib.Path, mode: str) -> BinaryIO:  # noqa VNE002
    check("b" in mode, AssertionError(f"|mode|(=={mode}) should contain 'b'"))
    if "w" in mode or "x" in mode:
        file_mkdirs_parent(file)
    # Type hint (no runtime check).
    result = cast(BinaryIO, open(str(file), mode))
    return result


def file_open_text(file: pathlib.Path, mode: str) -> TextIO:  # noqa VNE002
    check("b" not in mode, AssertionError(f"|mode|(=={mode}) should not contain 'b'"))
    if "w" in mode or "x" in mode:
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


def file_write_text_atomic(file: Path, text: str) -> Path:  # noqa VNE002
    """
    Writes |text| to |file| atomically by first writing to a temporary file alongside |file| then renaming the
    temporary file to |file|.

    :param file:
    :param text:
    :return:
    """
    temp_file: Path = file.parent / get_random_name()
    with file_open_text(temp_file, "x") as f:
        f.write(text)
        f.flush()
        os.fsync(f.fileno())
    # Will not fail if dest already exists; will just silently replace dest.
    os.replace(str(temp_file), str(file))

    return file


def file_write_text(file: pathlib.Path, text: str) -> Path:  # noqa VNE002
    with file_open_text(file, "w") as f:
        f.write(text)
    return file


def mkdir_p_new(path: Path) -> Path:  # noqa VNE002
    """
    Creates a directory (and all parents, if needed), but fails if the directory already exists.

    :param path: the path to create
    :return: the created path
    """

    file_mkdirs_parent(path)
    path.mkdir()
    return path


def mkdirs_p(path: pathlib.Path) -> Path:  # noqa VNE002
    # Use os.makedirs, as this is more likely to be atomic.
    os.makedirs(str(path), exist_ok=True)
    return path


def file_mkdirs_parent(file: pathlib.Path) -> None:  # noqa VNE002
    mkdirs_p(file.parent)


class ToolNotOnPathError(Exception):
    pass


def tool_on_path(tool: str, path: Optional[str] = None) -> pathlib.Path:  # noqa VNE002
    result = shutil.which(tool, path=path)
    if result is None:
        raise ToolNotOnPathError(
            "Could not find {} on PATH. Please add to PATH.".format(tool)
        )
    return pathlib.Path(result)


def prepend_catchsegv_if_available(
    cmd: List[str], log_warning: bool = False
) -> List[str]:
    try:
        return [str(tool_on_path("catchsegv"))] + cmd
    except ToolNotOnPathError:
        pass

    try:
        # cdb is the command line version of WinDbg.
        return [
            str(tool_on_path("cdb")),
            "-g",
            "-G",
            "-lines",
            "-nosqm",
            "-o",
            "-x",
            "-c",
            "kp;q",
        ] + cmd
    except ToolNotOnPathError:
        pass

    if log_warning:
        gflogging.log(
            "WARNING: Could not find catchsegv (Linux) or cdb (Windows) on your PATH; you will not be able to get "
            "stack traces from tools or the host driver."
        )

    return cmd


def copy_file_if_exists(source_file_path: Path, dest_file_path: Path) -> Optional[Path]:
    if not source_file_path.is_file():
        return None
    return copy_file(source_file_path, dest_file_path)


def copy_file(
    source_file_path: pathlib.Path, dest_file_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_file_path)
    gflogging.log(f"Copying {str(source_file_path)} to {str(dest_file_path)}")
    shutil.copy(str(source_file_path), str(dest_file_path))
    return dest_file_path


def copy_dir(
    source_dir_path: pathlib.Path, dest_dir_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_dir_path)
    shutil.copytree(source_dir_path, dest_dir_path)
    return dest_dir_path


def move_file(source_path: Path, dest_path: Path) -> Path:
    check_file_exists(source_path)
    check(
        not dest_path.is_dir(),
        AssertionError(
            f"Tried to move {str(source_path)} to a directory {str(dest_path)}"
        ),
    )
    file_mkdirs_parent(dest_path)
    gflogging.log(f"Move file {str(source_path)} to {str(dest_path)}")
    source_path.replace(dest_path)
    return dest_path


def move_dir(
    source_dir_path: pathlib.Path, dest_dir_path: pathlib.Path
) -> pathlib.Path:
    file_mkdirs_parent(dest_dir_path)
    shutil.move(source_dir_path, dest_dir_path)
    return dest_dir_path


def make_directory_symlink(new_symlink_file_path: Path, existing_dir: Path) -> Path:
    gflogging.log(f"symlink: from {str(new_symlink_file_path)} to {str(existing_dir)}")
    check(existing_dir.is_dir(), AssertionError(f"Not a directory: {existing_dir}"))
    file_mkdirs_parent(new_symlink_file_path)

    # symlink_to takes a path relative to the location of the new file (or an absolute path, but we avoid this).
    symlink_contents = os.path.relpath(
        str(existing_dir), start=str(new_symlink_file_path.parent)
    )
    try:
        new_symlink_file_path.symlink_to(symlink_contents, target_is_directory=True)
    except OSError:
        if get_platform() != "Windows":
            raise
        # Retry using junctions under Windows.
        try:
            # noinspection PyUnresolvedReferences
            import _winapi  # pylint: disable=import-error,import-outside-toplevel;

            # Unlike symlink_to, CreateJunction takes a path relative to the current directory.
            _winapi.CreateJunction(str(existing_dir), str(new_symlink_file_path))
            return new_symlink_file_path
        except ModuleNotFoundError:
            pass
        raise

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
    check(path.is_file(), FileNotFoundError(f'Could not find file "{str(path)}"'))


def check_dir_exists(path: Path) -> None:
    check(path.is_dir(), FileNotFoundError(f'Could not find directory "{str(path)}"'))


def get_platform() -> str:
    host = platform.system()
    if host in ("Linux", "Windows"):
        return host
    if host == "Darwin":
        return "Mac"
    raise AssertionError("Unsupported platform: {}".format(host))


def extract_archive(archive_file: Path, output_dir: Path) -> Path:
    """
    Extract/unpack an archive.

    :return: output_dir
    """
    gflogging.log(f"Extracting {str(archive_file)} to {str(output_dir)}")
    shutil.unpack_archive(str(archive_file), extract_dir=str(output_dir))
    gflogging.log("Done")
    return output_dir


@attr.dataclass
class ZipEntry:
    path: Path
    path_in_archive: Optional[Path] = None


def create_zip(output_file_path: Path, entries: List[ZipEntry]) -> Path:
    gflogging.log(f"Creating zip {str(output_file_path)}:")
    with zipfile.ZipFile(
        output_file_path, "w", compression=zipfile.ZIP_DEFLATED
    ) as file_handle:
        for entry in entries:
            file_handle.write(entry.path, entry.path_in_archive)
            gflogging.log(f"Adding: {entry.path} {entry.path_in_archive or ''}")
    return output_file_path


def get_random_name() -> str:
    return uuid.uuid4().hex


def update_gcov_environment_variable_if_needed() -> None:
    if "GCOV_PREFIX" in os.environ.keys():
        gcov_prefix: str = os.environ["GCOV_PREFIX"]
        if "PROC_ID" in gcov_prefix:
            pid = str(os.getpid())
            check(
                bool(pid),
                AssertionError(
                    "Failed to get process ID to replace PROC_ID in GCOV_PREFIX environment variable."
                ),
            )
            gcov_prefix = gcov_prefix.replace("PROC_ID", pid)
            os.environ["GCOV_PREFIX"] = gcov_prefix


HASH_CHUNK_SIZE = 1 << 27  # A few MBs.


def hash_file_str(file_path: Path, hashlib_hash: Optional[Any] = None) -> str:
    if not hashlib_hash:
        hashlib_hash = hashlib.sha256()
    hash_file(file_path, hashlib_hash)
    return hashlib_hash.hexdigest()


def hash_file(file_path: Path, hashlib_hash: Any) -> None:
    with file_open_binary(file_path, "rb") as f:
        while True:
            chunk = f.read(HASH_CHUNK_SIZE)
            if not chunk:
                break
            hashlib_hash.update(chunk)


class HashedCommand:
    """
    For creating a command (a list of str for subprocess) as well as a "hashed" version.

    The hashed version has each input file path replaced with the hash of its contents.
    This can be used with CommandCache to cache an output of running some program.
    """

    def __init__(self) -> None:
        self.cmd: List[str] = []
        self.hashed_cmd: List[str] = []

    def append_str(self, arg_str: str) -> None:
        self.cmd.append(arg_str)
        self.hashed_cmd.append(arg_str)

    def extend_str(self, arg_str: List[str]) -> None:
        self.cmd.extend(arg_str)
        self.hashed_cmd.extend(arg_str)

    def append_program_path(self, program_path: Path) -> None:
        # We can just store the path of the program.
        self.append_str(str(program_path))

    def append_input_file(self, input_file: Path) -> None:
        self.cmd.append(str(input_file))
        # The file extension (suffix) could be "part of the input".
        self.hashed_cmd.append(hash_file_str(input_file) + input_file.suffix)

    def append_output_file(self, output_file: Path) -> None:
        self.cmd.append(str(output_file))
        # The file extension (suffix) could be "part of the input".
        self.hashed_cmd.append("output" + output_file.suffix)


class CommandCache:
    """For caching the output of running a program."""

    def __init__(self) -> None:
        self.cache: Dict[Tuple[str, ...], Path] = {}

    def write_cached_output_file(
        self, cmd: HashedCommand, new_output_path: Path
    ) -> Optional[Path]:
        """
        Returns |new_output_path| if |cmd| was found in the cache and the cached output file was written to |new_output_path|.

        Otherwise, returns None.
        """
        cmd_tuple = tuple(cmd.hashed_cmd)
        try:
            gflogging.log(f"Preprocessor cache: {cmd_tuple}")
            cached_output_file_path = self.cache[cmd_tuple]
            gflogging.log(
                f"Preprocessor cache: hit {str(cached_output_file_path)} -> {str(new_output_path)}"
            )
            copy_file(cached_output_file_path, new_output_path)
            return new_output_path
        except KeyError:
            pass
        return None

    def add_output_to_cache(self, cmd: HashedCommand, output_path: Path) -> None:
        cmd_tuple = tuple(cmd.hashed_cmd)
        self.cache[cmd_tuple] = output_path
