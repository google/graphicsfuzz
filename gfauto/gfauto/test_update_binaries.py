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

"""Update binaries module.

A script that updates the binaries in a test.json file.
"""

import argparse
import itertools
import sys
from pathlib import Path
from typing import List

from gfauto import binaries_util, test_util
from gfauto.common_pb2 import Binary
from gfauto.gflogging import log
from gfauto.util import check_file_exists


def update_test_json(test_json: Path) -> Path:
    test = test_util.metadata_read_from_path(test_json)

    for test_binary in itertools.chain(
        test.binaries, test.device.binaries
    ):  # type: Binary
        for default_binary in binaries_util.DEFAULT_BINARIES:
            if (
                test_binary.name == default_binary.name
                and test_binary.version != default_binary.version
            ):
                log(
                    f"Updating version: {test_binary.version} -> {default_binary.version}"
                )
                test_binary.version = default_binary.version
                break

    return test_util.metadata_write_to_path(test, test_json)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="A script that updates the binaries in a test.json file."
    )

    parser.add_argument(
        "test_json", help="Paths to one or more test.json files.", nargs="*"
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    test_jsons: List[Path] = [Path(json_path) for json_path in parsed_args.test_json]

    for test_json in test_jsons:
        check_file_exists(test_json)

    for test_json in test_jsons:
        update_test_json(test_json)


if __name__ == "__main__":
    main()
    sys.exit(0)
