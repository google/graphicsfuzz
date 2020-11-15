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
    chunk_num: int, chunk: Set[str], log_files: List[Path], only_show_num_signatures: bool, look_for_spirv_opt_errors: bool, look_for_llpc_errors: bool, output_file: TextIO
) -> None:

    log(f"\nChunk {chunk_num}:")
    if not only_show_num_signatures:
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
                if look_for_spirv_opt_errors:
                    match = re.match(r"Command failed: \['/usr/bin/catchsegv', .*spirv-opt'", line)
                    if match:
                        found_bug = True
                        start_line = i + 1
                        # TODO: Is there a reliable way to find the end of a spirv-opt failure?
                        end_line = len(lines) - 1
                        break
                elif look_for_llpc_errors:
                    match = re.match(r"Command failed: \['/usr/bin/catchsegv', '.*amdllpc", line)
                    if match:
                        found_bug = True
                        start_line = i + 1
                        # TODO: Is there a reliable way to find the end of an LLPC failure?
                        end_line = len(lines) - 1
                        break
                else:
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

    if only_show_num_signatures:
        # Print number of unique signatures.
        log(str(len(unique_signatures)) + '\n')
        output_file.write(f"{len(unique_signatures)}\n")
    else:
        # Print the unique signatures.
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

    parser.add_argument(
        "--chunk_size", help="Chunk size.", type=int, default=1_000,

    )

    parser.add_argument(
        "--only_show_num_signatures", help="Output only the number of distinct signatures per chunk, one integer per line.  This is useful for gathering statistics.", default=False, action='store_true'
    )

    parser.add_argument(
        "--spirv_opt", help="Look for spirv-opt errors.", default=False, action='store_true'
    )

    parser.add_argument(
        "--llpc", help="Look for LLPC errors.", default=False, action='store_true'
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    seed_file: Path = Path(parsed_args.seed_file)
    output_file: Path = Path(parsed_args.out)
    chunk_size: int = parsed_args.chunk_size
    only_show_num_signatures: bool = parsed_args.only_show_num_signatures
    look_for_spirv_opt_errors: bool = parsed_args.spirv_opt
    look_for_llpc_errors: bool = parsed_args.llpc

    check(not (look_for_spirv_opt_errors and look_for_llpc_errors), AssertionError("At most one of --spirv_opt and --llpc can be used"))

    # Get a list of all log files.
    log_files: List[Path] = sorted(Path().glob("log_*.txt"))

    # Get chunks of seeds and call process_chunk.
    seeds: List[str] = util.file_read_text(seed_file).split()

    check((len(seeds) % chunk_size) == 0, AssertionError("The number of seeds should be a multiple of chunk_size."))

    with util.file_open_text(output_file, "w") as output:
        index = 0
        for chunk_num in range(0, len(seeds) // chunk_size):
            chunk: Set[str] = set()
            for _ in range(0, chunk_size):
                chunk.add(seeds[index])
                index += 1
            process_chunk(chunk_num, chunk, log_files, only_show_num_signatures, look_for_spirv_opt_errors, look_for_llpc_errors, output)

        check(
            index == len(seeds), AssertionError("Expected to have processed all seeds.")
        )


if __name__ == "__main__":
    main()
