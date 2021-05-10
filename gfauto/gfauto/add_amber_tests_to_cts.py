#!/usr/bin/env python3
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

"""Add amber tests to CTS.

This module/script adds Amber test files to the VK-GL-CTS project.
This file is self-contained so it can be provided alongside Amber test files.
"""

import argparse
import os
import re
import shutil
import sys
from typing import Pattern, TextIO, cast

SHORT_DESCRIPTION_LINE_PREFIX = "# Short description: "

MUST_PASS_PATHS = [
    os.path.join("android", "cts", "master", "vk-master", "graphicsfuzz.txt"),
    os.path.join(
        "android", "cts", "master", "vk-master-2021-03-01", "graphicsfuzz.txt"
    ),
    os.path.join(
        "external", "vulkancts", "mustpass", "master", "vk-default", "graphicsfuzz.txt"
    ),
]

TEST_NAME_IN_LINE_PATTERN: Pattern[str] = re.compile(r"\"(.*?)\.amber\"")


def check(condition: bool, exception: Exception) -> None:
    if not condition:
        raise exception


def log(message: str = "") -> None:
    print(message, flush=True)  # noqa: T001


def remove_start(string: str, start: str) -> str:
    check(
        string.startswith(start), AssertionError("|string| does not start with |start|")
    )

    return string[len(start) :]


def open_helper(file: str, mode: str) -> TextIO:  # noqa VNE002
    check("b" not in mode, AssertionError(f"|mode|(=={mode}) should not contain 'b'"))
    return cast(TextIO, open(file, mode, encoding="utf-8", errors="ignore"))


def check_dir_exists(directory: str) -> None:
    log("Checking that directory {} exists".format(directory))
    if not os.path.isdir(directory):
        raise FileNotFoundError("Directory not found: {}".format(directory))


def check_file_exists(file: str) -> None:  # noqa VNE002
    log("Checking that file {} exists".format(file))
    if not os.path.isfile(file):
        raise FileNotFoundError("File not found: {}".format(file))


def get_amber_test_file_path(vk_gl_cts: str, amber_test_name: str) -> str:
    return os.path.join(
        vk_gl_cts,
        "external",
        "vulkancts",
        "data",
        "vulkan",
        "amber",
        "graphicsfuzz",
        amber_test_name + ".amber",
    )


def get_graphics_fuzz_tests_index_file_path(vk_gl_cts: str) -> str:
    return os.path.join(
        vk_gl_cts,
        "external",
        "vulkancts",
        "data",
        "vulkan",
        "amber",
        "graphicsfuzz",
        "index.txt",
    )


def get_amber_test_short_description(amber_test_file_path: str) -> str:
    with open_helper(amber_test_file_path, "r") as f:
        for line in f:
            if line.startswith(SHORT_DESCRIPTION_LINE_PREFIX):
                line = remove_start(line, SHORT_DESCRIPTION_LINE_PREFIX)
                # Remove \n
                line = line[:-1]
                return line
    return ""


def check_and_add_tabs(
    line: str, string_name: str, string_value: str, field_index: int, tab_size: int
) -> str:

    # Field index starts at 1. Change it to start at 0.
    field_index -= 1

    check(
        len(line.expandtabs(tab_size)) <= field_index,
        AssertionError('{} "{}" is too long!'.format(string_name, string_value)),
    )

    while len(line.expandtabs(tab_size)) < field_index:
        line += "\t"

    check(
        len(line.expandtabs(tab_size)) == field_index,
        AssertionError(
            "Field index {} is incorrect; Python script needs fixing".format(
                field_index
            )
        ),
    )

    return line


def get_index_line_to_write(amber_test_name: str, short_description: str) -> str:

    # A test line has the following form, except with tabs aligning each field.
    # { "name.amber", "name", "description" },
    #   |             |       |             |
    #   1             2       3             4

    # 1
    test_file_name_start_index = 5
    # 2
    test_name_start_index = 97
    # 3
    test_description_start_index = 181
    # 4
    test_close_bracket_index = 265

    tab_size = 4

    line = "{"

    line = check_and_add_tabs(
        line, "internal", "internal", test_file_name_start_index, tab_size
    )

    line += '"{}.amber",'.format(amber_test_name)

    line = check_and_add_tabs(
        line, "amber test name", amber_test_name, test_name_start_index, tab_size
    )

    line += '"{}",'.format(amber_test_name)

    line = check_and_add_tabs(
        line, "amber test name", amber_test_name, test_description_start_index, tab_size
    )

    line += '"{}"'.format(short_description)

    line = check_and_add_tabs(
        line, "short description", short_description, test_close_bracket_index, tab_size
    )

    line += "},\n"

    return line


def add_amber_test_to_index_file(
    vk_gl_cts: str, amber_test_name: str, input_amber_test_file_path: str
) -> None:
    log("Adding Amber test to the index file.")

    short_description = get_amber_test_short_description(input_amber_test_file_path)

    index_file = get_graphics_fuzz_tests_index_file_path(vk_gl_cts)
    index_file_bak = index_file + ".bak"

    copyfile(index_file, index_file_bak)

    line_to_write = get_index_line_to_write(amber_test_name, short_description)

    log("Writing from {} to {}.".format(index_file_bak, index_file))

    with open_helper(index_file_bak, "r") as index_file_in:
        with open_helper(index_file, "w") as index_file_out:

            # {    "continue-and-merge.amber",...

            # Get to the point where we should insert our line.
            line = ""
            for line in index_file_in:
                if line.startswith("{"):
                    result = TEST_NAME_IN_LINE_PATTERN.search(line)
                    if result:
                        # Group 1 is the test name.
                        test_name_in_line = result.group(1)
                        if test_name_in_line >= amber_test_name:
                            break
                index_file_out.write(line)

            # Write our line and then the previously read line.

            # Don't write the line if it already exists; idempotent.
            if line != line_to_write:
                log("Writing line: {}".format(line_to_write[:-1]))
                index_file_out.write(line_to_write)
            else:
                log("Line already exists.")
            index_file_out.write(line)

            # Write the remaining lines.
            for line in index_file_in:
                index_file_out.write(line)

    remove(index_file_bak)


def add_amber_test_to_must_pass(amber_test_name: str, must_pass_file_path: str) -> None:
    log("Adding the Amber test to {}".format(must_pass_file_path))

    must_pass_file_path_bak = must_pass_file_path + ".bak"
    copyfile(must_pass_file_path, must_pass_file_path_bak)

    line_to_write = "dEQP-VK.graphicsfuzz.{}\n".format(amber_test_name)

    log("Writing from {} to {}.".format(must_pass_file_path_bak, must_pass_file_path))

    with open_helper(must_pass_file_path_bak, "r") as pass_in:
        with open_helper(must_pass_file_path, "w") as pass_out:
            # Get to just before the first GraphicsFuzz test.
            line = ""
            for line in pass_in:
                if line.startswith("dEQP-VK.graphicsfuzz."):
                    break
                pass_out.write(line)

            # |line| contains an unwritten line.
            # Get to the point where we need to write line_to_write.
            while True:
                if (not len) or line >= line_to_write:
                    break
                pass_out.write(line)
                line = pass_in.readline()

            # Don't write the line if it already exists; idempotent.
            if line != line_to_write:
                log("Writing line: {}".format(line_to_write[:-1]))
                pass_out.write(line_to_write)
            else:
                log("Line already exists.")
            pass_out.write(line)

            # Write remaining lines.
            for line in pass_in:
                pass_out.write(line)

    remove(must_pass_file_path_bak)


def copyfile(source: str, dest: str) -> None:
    log("Copying {} to {}".format(source, dest))
    shutil.copyfile(source, dest)


def remove(file: str) -> None:  # noqa VNE002
    log("Deleting {}".format(file))
    os.remove(file)


def copy_amber_test_file(
    vk_gl_cts: str, amber_test_name: str, input_amber_test_file_path: str
) -> None:
    log("Copying Amber test file")

    amber_test_file_path = get_amber_test_file_path(vk_gl_cts, amber_test_name)

    check_dir_exists(os.path.dirname(amber_test_file_path))

    copyfile(input_amber_test_file_path, amber_test_file_path)


def add_amber_test(input_amber_test_file_path: str, vk_gl_cts: str) -> None:
    log('Adding Amber test "{}" to "{}"'.format(input_amber_test_file_path, vk_gl_cts))
    # E.g. "continue-and-merge"
    amber_test_name = os.path.basename(input_amber_test_file_path)
    amber_test_name = os.path.splitext(amber_test_name)[0]

    log('Using test name "{}"'.format(amber_test_name))

    add_amber_test_to_index_file(vk_gl_cts, amber_test_name, input_amber_test_file_path)

    copy_amber_test_file(vk_gl_cts, amber_test_name, input_amber_test_file_path)

    for must_pass_file_path in MUST_PASS_PATHS:
        add_amber_test_to_must_pass(
            amber_test_name, os.path.join(vk_gl_cts, must_pass_file_path)
        )


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A script to add Amber tests to the CTS."
    )

    parser.add_argument("vk_gl_cts", help="Path to a checkout of VK-GL-CTS")

    parser.add_argument(
        "amber_files",
        help="One or more Amber test files (often ending in .amber_script, .amber, .vkscript)",
        nargs="+",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    vk_gl_cts = parsed_args.vk_gl_cts
    amber_files = parsed_args.amber_files

    check_dir_exists(vk_gl_cts)
    check_file_exists(get_graphics_fuzz_tests_index_file_path(vk_gl_cts))

    for amber_file in amber_files:
        add_amber_test(amber_file, vk_gl_cts)


if __name__ == "__main__":
    main()
    sys.exit(0)
