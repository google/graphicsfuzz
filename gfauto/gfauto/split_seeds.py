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

"""Splits a list of seeds."""

import argparse
import sys
from pathlib import Path
from typing import List

from gfauto import util
from gfauto.util import check


def main() -> None:
    parser = argparse.ArgumentParser(description="Splits a seed file.")

    parser.add_argument(
        "seed_file", help="Seed file to process.",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    seed_file: Path = Path(parsed_args.seed_file)

    # Get chunks of seeds and call process_chunk.
    seeds: List[str] = util.file_read_text(seed_file).split()

    chunk_size = 1000

    check(
        (len(seeds) % chunk_size) == 0,
        AssertionError("The number of seeds should be a multiple of chunk_size."),
    )

    index = 0
    for chunk_num in range(0, len(seeds) // chunk_size):
        chunk: List[str] = []
        for _ in range(0, chunk_size):
            chunk.append(seeds[index])
            index += 1
        util.file_write_text(Path(f"seeds_{chunk_num}.txt"), " ".join(chunk) + "\n")

    check(index == len(seeds), AssertionError("Expected to have processed all seeds."))


if __name__ == "__main__":
    main()
