#!/usr/bin/env python3

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
import shutil
import subprocess
from typing import List

import runspv


def open_helper(file: str, mode: str):
    """
    Helper function to open a file with UTF-8 format, ignoring errors.
    Wraps around runspv.open_helper(), which wraps open() in turn.
    :param file: the file to open - same as the file argument of open().
    :param mode: the mode to open the file in - same as the mode argument of open().
    :return: a File object opened in UTF-8 format and ignoring errors.
    """
    return runspv.open_helper(file, mode)


def open_bin_helper(file: str, mode: str):
    """
    Helper function to open a file in binary mode. Wraps around runspv.open_bin_helper().
    Will cause an assertion failure if 'mode' does not contain the open() binary symbol 'b'.
    :param file: the file to open - same as the file argument of open().
    :param mode: the mode to open the file in - same as the mode argument of open().
    :return: a File object opened in binary mode.
    """
    return runspv.open_bin_helper(file, mode)


def get_platform() -> str:
    """
    Helper function to determine the current platform in use. Wraps around runspv.get_platform().
    :return: the platform in use. Throws AssertionError if platform is not Linux/Windows/Mac.
    """
    return runspv.get_platform()


def get_bin_dir():
    """
    Helper function to get the directory of binaries (e.g. glslangvalidator) for the current
    platform. Wraps around runspv.get_bin_dir().
    :return: the path of binaries for the platform in use.
    """
    return runspv.get_bin_dir()


def tool_on_path(tool: str) -> str:
    """
    Helper function to determine if a given tool is on the user's PATH variable. Wraps around
    runspv.tool_on_path().
    :param tool: the tool's filename to look for.
    :return: the path of the tool, else ToolNotOnPathError if the tool isn't on the PATH.
    """
    return runspv.tool_on_path(tool)


ToolNotOnPathError = runspv.ToolNotOnPathError


def remove_end(str_in: str, str_end: str) -> str:
    """
    Helper function to remove the end of a string. Useful for removing file extensions if you
    know what extension your file should be. Wraps around runspv.remove_end().
    :param str_in: the string to remove the end from.
    :param str_end: the suffix that you want to remove from str_in.
    :return: str_in with str_end removed.
    """
    return runspv.remove_end(str_in, str_end)


def filename_extension_suggests_glsl(file: str) -> bool:
    """
    Helper function to determine if a file is a vertex, fragment, or compute shader. Wraps around
    runspv.filename_extension_suggests_glsl().
    :param file: The filename/path to check.
    :return: True if the file is a vertex/fragment/compute shader file, false otherwise.
    """
    return runspv.filename_extension_suggests_glsl(file)


def write_to_file(content, filename) -> None:
    """
    Helper function to write a string to a file opened in UTF-8 format.
    :param content: The string to write to the file.
    :param filename: The name/path of the file to write to.
    """
    with open_helper(filename, 'w') as f:
        f.write(content)


def remove(file) -> None:
    """
    Helper function to delete a file. If the file is a directory, the directory will be recursively
    deleted.
    :param file: The file to be deleted.
    """
    if os.path.isdir(file):
        shutil.rmtree(file)
    elif os.path.isfile(file):
        os.remove(file)


def check_input_files_exist(filenames: List[str]) -> None:
    """
    Helper function to determine if a list of files all exist.
    :param filenames: The list of filenames to check.
    :return: Nothing - throws FileNotFoundError if a file is not found.
    """
    for filename in filenames:
        if not os.path.isfile(filename):
            raise FileNotFoundError('Input file "' + filename + '" not found')


def remove_start(s: str, start: str):
    """
    Helper function to remove the beginning of a string.
    :param s: the string to remove the prefix from.
    :param start: the prefix of the string to remove.
    :return: s with the start of the string removed.
    """
    assert s.startswith(start)
    return s[len(start):]


def subprocess_helper(cmd: List[str], check=True, timeout=None, verbose=False) \
                   -> subprocess.CompletedProcess:
    """
    Helper function to execute a command in the shell. Wraps around runspv.subprocess_helper().
    :param cmd: the command (and its arguments) to execute.
    :param check: whether to throw CalledProcessError if a non-zero returncode is issued from the
                  process. Defaults to True.
    :param timeout: time to wait in seconds before killing process and throwing TimeoutExpired.
                    Defaults to None.
    :param verbose: whether to log process stdout/stderr more extensively. Defaults to False.
    :return: a subprocess.CompletedProcess object.
    """
    return runspv.subprocess_helper(cmd, check, timeout, verbose)


def run_catchsegv(cmd: List[str], timeout=None, verbose=False) -> str:
    """
    Helper function similar to subprocess_helper, but is able to handle the killing of child
    processes as well. This is most useful when trying to capture segmentation faults, as you can
    run 'catchsegv (command)' in shell to ensure that the segfault is logged. Wraps around
    runspv.run_catchsegv().
    :param cmd: the command (and its arguments) to execute.
    :param timeout: time to wait in seconds before killing process and throwing TimeoutExpired.
                    Defaults to None.
    :param verbose: whether to log process stdout/stderr more extensively. Defaults to False.
    :return: the status of the process after running - 'SUCCESS', 'TIMEOUT', or 'CRASH'.
    """
    return runspv.run_catchsegv(cmd, timeout, verbose)


def log(message: str):
    """
    Helper function to output a message to stdout, and optionally log to a file if set_logfile()
    was used previously to set a global logfile. Wraps around runspv.log().
    :param message: the message to log to stdout/file.
    """
    runspv.log(message)


def set_logfile(file):
    """
    Helper function to set the global logfile. Wraps around runspv.log_to_file. Workaround for
    runspv.log() using a global logfile.
    :param file: the file to log to - must be opened already.
    """
    runspv.log_to_file = file


def unset_logfile():
    """
    Helper function to unset the global logfile. Wraps around runspv.log_to_file. Workaround for
    runspv.log() using a global logfile.
    """
    runspv.log_to_file = None
