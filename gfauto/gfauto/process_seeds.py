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
from typing import List, Set, TextIO

from gfauto import signature_util, util
from gfauto.gflogging import log
from gfauto.util import check


def process_chunk(  # pylint: disable=too-many-locals;
    chunk_num: int, chunk: Set[str], log_files: List[Path], output_file: TextIO
) -> None:

    log(f"\nChunk {chunk_num}:")
    output_file.write(f"\nChunk {chunk_num}:\n")

    unique_signatures: Set[str] = set()

    for log_file in log_files:
        with util.file_open_text(log_file, "r") as f:
            first_line = f.readline()
            match = re.fullmatch(r"Iteration seed: (\d+)\n", first_line)
            assert match  # noqa
            seed = match.group(1)
            if seed not in chunk:
                continue

            lines = f.readlines()
            start_line = 0
            end_line = 0
            found_bug = False
            for i, line in enumerate(lines):
                match = re.fullmatch(r"STATUS (\w+)\n", line)
                if not match:
                    continue
                status = match.group(1)
                if status == "SUCCESS":
                    start_line = i + 1
                    continue
                found_bug = True
                end_line = i + 1
                break

            if not found_bug:
                continue

            failure_log = "\n".join(lines[start_line:end_line])

            signature = signature_util.get_signature_from_log_contents(failure_log)

            unique_signatures.add(signature)

    # Print the signatures.
    for signature in sorted(unique_signatures):
        log(signature)
        output_file.write(f"{signature}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Processes a seed file.")

    parser.add_argument(
        "seed_file", help="Seed file to process.",
    )

    parser.add_argument(
        "--out", help="Output file.", default="signatures_chunked.txt",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    seed_file: Path = Path(parsed_args.seed_file)
    output_file: Path = Path(parsed_args.out)

    # Get a list of all log files.
    log_files: List[Path] = sorted(Path().glob("log_*.txt"))

    # Get chunks of seeds and call process_chunk.
    seeds: List[str] = util.file_read_text(seed_file).split()

    check(len(seeds) == 10_000, AssertionError("Expected 10,000 seeds."))

    with util.file_open_text(output_file, "w") as output:
        index = 0
        for chunk_num in range(0, 10):
            chunk: Set[str] = set()
            for _ in range(0, 1_000):
                chunk.add(seeds[index])
                index += 1
            process_chunk(chunk_num, chunk, log_files, output)

        check(
            index == 10_000, AssertionError("Expected to have processed 10,000 seeds.")
        )


if __name__ == "__main__":
    main()
