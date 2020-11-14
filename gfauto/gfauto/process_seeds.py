# -*- coding: utf-8 -*-

# Copyright 2020 The GraphicsFuzz Project Authors
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

"""Processes a list of seeds; outputs the crash signatures for chunks of seeds."""

import argparse
import re
import sys
from pathlib import Path
from typing import Dict, List, Set

from gfauto import util
from gfauto.gflogging import log
from gfauto.util import check

CRASHES_DIR = Path() / "reports" / "crashes"
TOOL_CRASHES_DIR = Path() / "reports" / "tool_crashes"
UNRESPONSIVE_DIR = Path() / "reports" / "unresponsive"


def process_chunk(
    chunk_num: int,
    chunk: Set[str],
    test_name_to_signature: Dict[str, str],
    log_files: List[Path],
) -> None:

    log(f"\nChunk {chunk_num}:")

    # Example line from log file:
    #
    #                          |-- test name -----|
    # Amberfy: ['temp/e45c5783/e45c5783_no_opt_test/results/host/result/variant/...  # noqa: SC100, SC200

    unique_signatures: Set[str] = set()

    for log_file in log_files:
        with util.file_open_text(log_file, "r") as f:
            first_line = f.readline()
            match = re.fullmatch(r"Iteration seed: (\d+)\n", first_line)
            assert match  # noqa
            seed = match.group(1)
            if seed not in chunk:
                continue
            for line in f:
                if not line.startswith("Amberfy: ['temp"):
                    continue
                match = re.fullmatch(
                    r"Amberfy: \['temp/[^/]+/([^/]+)/results/([^/]+)/.*\n", line
                )
                assert match, line  # noqa
                # Concatenate the original test name (without the device name) with the device name.
                test_name = f"{match.group(1)}_{match.group(2)}"
                if test_name not in test_name_to_signature:
                    continue
                unique_signatures.add(test_name_to_signature[test_name])

    # Print the signatures.
    for signature in sorted(unique_signatures):
        log(signature)


def main() -> None:
    parser = argparse.ArgumentParser(description="Processes a seed file.")

    parser.add_argument(
        "seed_file", help="Seed file to process.",
    )

    parser.add_argument(
        "--tool_crashes",
        help=f"Look in {str(TOOL_CRASHES_DIR)}. Otherwise, we look in {CRASHES_DIR} and {UNRESPONSIVE_DIR}.",
        action="store_true",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    seed_file: Path = Path(parsed_args.seed_file)
    just_tool_crashes: bool = parsed_args.tool_crashes

    dirs: List[Path] = []

    if just_tool_crashes:
        dirs = [TOOL_CRASHES_DIR]
    else:
        dirs.append(CRASHES_DIR)
        if UNRESPONSIVE_DIR.is_dir():
            dirs.append(UNRESPONSIVE_DIR)

    # Get all test names and map them to a signature.
    # E.g. reports/compile_error/0c32b3e4_no_opt_test_host gives:  # noqa: SC100, SC200
    # 0c32b3e4_no_opt_test_host -> compile_error  # noqa: SC100, SC200

    log("Creating test name to signature map.")

    test_name_to_signature: Dict[str, str] = {}

    for buckets_dir in dirs:
        log(f"Checking {str(buckets_dir)}")
        test_dirs: List[Path] = sorted(buckets_dir.glob("*/*"))
        test_dirs = [t for t in test_dirs if t.is_dir()]
        for test_dir in test_dirs:
            test_name_to_signature[test_dir.parts[-1]] = test_dir.parts[-2]

    check(
        len(test_name_to_signature) > 0,
        AssertionError("Empty test name to signature map!"),
    )

    log("Signature map done.")

    # Get a list of all log files.
    log_files: List[Path] = sorted(Path().glob("log_*.txt"))

    # Get chunks of seeds and call process_chunk.
    seeds: List[str] = util.file_read_text(seed_file).split()

    check(len(seeds) == 10_000, AssertionError("Expected 10,000 seeds."))

    index = 0
    for chunk_num in range(0, 10):
        chunk: Set[str] = set()
        for _ in range(0, 1_000):
            chunk.add(seeds[index])
            index += 1
        process_chunk(chunk_num, chunk, test_name_to_signature, log_files)

    check(index == 10_000, AssertionError("Expected to have processed 10,000 seeds."))


if __name__ == "__main__":
    main()
