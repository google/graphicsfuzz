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

"""Processes coverage files."""

import argparse
import io
import os
import random
import re
import shutil
import string
import subprocess
import threading
import timeit
import typing
from collections import Counter
from queue import Queue
from typing import Dict, List, Tuple

import array
from attr import dataclass

# Type:
from gfauto import util

LineCounts = Dict[str, typing.Counter[int]]

DirAndItsFiles = Tuple[str, List[str]]

DirAndItsOutput = Tuple[str, str]


@dataclass
class GetLineCountsData:
    gcov_path: str
    gcov_uses_json_output: bool
    build_dir: str
    num_threads: int
    gcda_files_queue: "Queue[DirAndItsFiles]" = Queue()
    stdout_queue: "Queue[DirAndItsOutput]" = Queue()
    line_counts: LineCounts = {}


def _thread_gcov(data: GetLineCountsData) -> None:
    # Keep getting files and processing until the special "done" ("", []) message.

    while True:
        root, files = data.gcda_files_queue.get()
        if not root:
            # This is the special "done" message.
            break
        cmd = [data.gcov_path, "-i"]
        if data.gcov_uses_json_output:
            cmd.append("-t")
        cmd.extend(files)
        # I.e.: cd $root && gcov -i -t file1.gcda file2.gcda ...
        result = subprocess.run(
            cmd,
            encoding="utf-8",
            errors="ignore",
            check=True,
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        if data.gcov_uses_json_output:
            data.stdout_queue.put((root, result.stdout))
        else:
            gcov_files = [file + ".gcov" for file in files]
            gcov_contents = []
            for gcov_file in gcov_files:
                with open(
                    os.path.join(root, gcov_file),
                    "r",
                    encoding="utf-8",
                    errors="ignore",
                ) as f:
                    gcov_contents.append(f.read())
            gcov_contents_combined = "\n".join(gcov_contents)
            data.stdout_queue.put((root, gcov_contents_combined))


def _thread_gcovs(data: GetLineCountsData) -> None:
    threads = [
        threading.Thread(target=_thread_gcov, args=(data,))
        for _ in range(data.num_threads)
    ]
    for thread in threads:
        thread.start()

    for thread in threads:
        thread.join()

    # send "done" message to adder thread.
    data.stdout_queue.put(("", ""))


def _process_text_lines(
    data: GetLineCountsData, root: str, lines: typing.TextIO
) -> None:
    current_file = ""
    current_file_line_counts: typing.Counter[int] = Counter()
    for line in lines:
        line = line.strip()
        # file:file_path
        if line.startswith("file:"):
            current_file = line[5:]
            current_file_line_counts = data.line_counts.setdefault(
                current_file, Counter()
            )
            continue
        # lcount:line number,execution_count,has_unexecuted_block
        if line.startswith("lcount:"):
            assert current_file
            line = line[7:]
            parts = line.split(sep=",")
            line_number = int(parts[0])
            count = int(parts[1])
            # Confusingly, |update| adds counts.
            current_file_line_counts.update({line_number: count})
            continue


def _process_json_lines(
    data: GetLineCountsData, root: str, lines: typing.TextIO
) -> None:
    current_file = ""
    current_file_line_counts: typing.Counter[int] = Counter()
    # The count value given by the previous line, which may or may not be used depending on the next line.
    current_count = -1
    for line in lines:
        line = line.strip()
        # "file": "file_name",
        if line.startswith('"file": "'):
            assert current_count < 0
            current_file = line[9:-2]
            current_file_line_counts = data.line_counts.setdefault(
                current_file, Counter()
            )
            continue
        # "count": count,
        if line.startswith('"count": '):
            assert current_file
            current_count = int(line[9:-1])
            continue
        # "line_number": line_number,
        if line.startswith('"line_number": '):
            assert current_file
            assert current_count >= 0
            line_number = int(line[15:-1])
            # Confusingly, |update| adds counts.
            current_file_line_counts.update({line_number: current_count})
            # Fallthrough.

        # Reset current_count; the "count" field can occur in a few places, but we only want to use the count that
        # is immediately followed by "line_number".
        current_count = -1


def _thread_adder(data: GetLineCountsData) -> None:
    # Keep processing stdout entries until we get the special "done" message.

    while True:
        root, stdout = data.stdout_queue.get()
        if not root:
            # This is the special "done" message.
            break
        lines = io.StringIO(stdout)

        if data.gcov_uses_json_output:
            _process_json_lines(data, root, lines)
        else:
            _process_text_lines(data, root, lines)


def get_line_counts(data: GetLineCountsData) -> None:

    gcovs_thread = threading.Thread(target=_thread_gcovs, args=(data,))
    adder_thread = threading.Thread(target=_thread_adder, args=(data,))
    a = array.array("L")

    gcovs_thread.start()
    adder_thread.start()

    root: str
    dirs: List[str]
    files: List[str]
    for root, dirs, files in os.walk(data.build_dir):
        gcda_files = [f for f in files if f.endswith(".gcno")]
        # TODO: Could split further if necessary.
        if gcda_files:
            data.gcda_files_queue.put((os.path.join(root), gcda_files))

    # Send a "done" message for each thread.
    for _ in range(data.num_threads):
        data.gcda_files_queue.put(("", []))

    # wait for threads.
    gcovs_thread.join()
    adder_thread.join()


def main() -> None:

    gcov_path = shutil.which("gcov")
    assert gcov_path
    data = GetLineCountsData(
        gcov_path=gcov_path,
        gcov_uses_json_output=False,
        build_dir="/data/git/SwiftShader/out/build_cov",
        num_threads=1,
    )

    get_line_counts(data)

    for k, v in data.line_counts.items():
        print(k)
        counter = 0
        print(len(v.items()))
        # for line, count in v.items():
        #     print(f"Line {line}, count {count}")
        #     counter += 1
        #     if counter > 4:
        #         break

    # parser = argparse.ArgumentParser(
    #     description="Runs a binary given the binary name and settings.json file."
    # )
    #
    # parser.add_argument(
    #     "--settings",
    #     help="Path to the settings JSON file for this instance.",
    #     default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    # )
    #
    # parser.add_argument(
    #     "binary_name",
    #     help="The name of the binary to run. E.g. spirv-opt, glslangValidator",
    #     type=str,
    # )
    #
    # parser.add_argument(
    #     "arguments",
    #     metavar="arguments",
    #     type=str,
    #     nargs="*",
    #     help="The arguments to pass to the binary",
    # )
    #
    # parsed_args = parser.parse_args(sys.argv[1:])


if __name__ == "__main__":
    main()
