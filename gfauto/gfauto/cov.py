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
import glob
import os
import threading
from queue import Queue
from typing import Dict, Counter, List, Tuple

# Type:
LineCounts = Dict[Counter[str]]

DirAndItsFiles = Tuple[str, List[str]]


def _thread_gcov(gcda_files_queue: Queue[DirAndItsFiles]):
    # keep getting chunk and processing.
    # until "done" ("", [])
    print("hello")


def _thread_gcovs(num_threads: int, gcda_files_queue: Queue[DirAndItsFiles]):
    threads = [
        threading.Thread(target=_thread_gcov, args=[gcda_files_queue])
        for _ in range(num_threads)
    ]
    for thread in threads:
        thread.start()

    for thread in threads:
        thread.join()

    # send "done" message to adder.


def _thread_adder():
    # keep adding together.
    # until "done"
    print("hello")


def get_line_counts(
    build_dir: str, chunk_size: int = 100, num_threads: int = 4
) -> LineCounts:
    line_counts: LineCounts = {}
    gcda_files_queue: Queue[DirAndItsFiles] = Queue()
    adder_queue: Queue[LineCounts] = Queue()

    gcovs_thread = threading.Thread(
        target=_thread_gcovs, args=[num_threads, gcda_files_queue]
    )
    gcovs_thread.start()

    adder_thread = threading.Thread(target=_thread_adder, args=[])
    adder_thread.start()

    root: str
    dirs: List[str]
    files: List[str]
    for root, dirs, files in os.walk(build_dir):
        gcda_files = [f for f in files if f.endswith(".gcda")]
        gcda_files_queue.put((os.path.join(root), gcda_files))

    # Send "done" message.
    gcda_files_queue.XXXX

    # wait for threads.
    gcovs_thread.join()
    adder_thread.join()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Runs a binary given the binary name and settings.json file."
    )

    parser.add_argument(
        "--settings",
        help="Path to the settings JSON file for this instance.",
        default=str(settings_util.DEFAULT_SETTINGS_FILE_PATH),
    )

    parser.add_argument(
        "binary_name",
        help="The name of the binary to run. E.g. spirv-opt, glslangValidator",
        type=str,
    )

    parser.add_argument(
        "arguments",
        metavar="arguments",
        type=str,
        nargs="*",
        help="The arguments to pass to the binary",
    )

    parsed_args = parser.parse_args(sys.argv[1:])


if __name__ == "__main__":
    main()
