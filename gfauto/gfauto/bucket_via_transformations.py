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

"""Classifies spirv-fuzz tests via the set of remaining transformation types."""

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List, Set

from gfauto import util
from gfauto.gflogging import log
from gfauto.util import check, check_dir_exists

COMMON_TRANSFORMATION_TYPES = {
    "addConstantBoolean",
    "addConstantComposite",
    "addConstantNull",
    "addConstantScalar",
    "addDeadBlock",
    "addDeadBreak",
    "addFunction",
    "addGlobalUndef",
    "addGlobalVariable",
    "addLocalVariable",
    "addTypeArray",
    "addTypeBoolean",
    "addTypeFloat",
    "addTypeFunction",
    "addTypeInt",
    "addTypeMatrix",
    "addTypePointer",
    "addTypeStruct",
    "addTypeVector",
    "computeDataSynonymFactClosure",
    "splitBlock",
}


def main() -> None:  # pylint: disable=too-many-locals;
    parser = argparse.ArgumentParser(
        description="Classifies spirv-fuzz tests via the set of remaining transformation types."
    )

    parser.add_argument(
        "--tests_dir",
        help="The directory in which to search for tests by looking for summary/ directories.",
        default=str(Path("") / "reports" / "crashes" / "bad_image"),
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    tests_dir: Path = Path(parsed_args.tests_dir)

    check_dir_exists(tests_dir)

    summary_dirs: List[Path] = list(tests_dir.glob("**/summary"))
    summary_dirs = [d for d in summary_dirs if d.is_dir()]
    summary_dirs = sorted(summary_dirs)

    check(
        bool(summary_dirs),
        AssertionError(f"No summary dirs found under {str(tests_dir)}"),
    )

    signature_to_dirs: Dict[str, List[Path]] = {}
    # For each summary directory, get its signature based on the transformation types remaining
    # and add the info to |signature_to_dirs|.
    for summary_dir in summary_dirs:
        log(f"Checking {summary_dir}")
        transformations_json = util.file_read_text(
            summary_dir / "reduced_1" / "variant" / "shader.frag.transformations_json"
        )
        transformations = json.loads(transformations_json)

        # E.g.
        # {
        #  "transformation": [
        #   {
        #    "addConstantScalar": {...}
        #   },
        #   {
        #    "addConstantComposite": {...}
        #   },
        #   ...,
        # }

        transformation_types: Set[str] = set()

        transformation_list = transformations["transformation"]

        check(
            bool(transformation_list),
            AssertionError(f"No transformations found for {str(transformations_json)}"),
        )

        for transformation in transformation_list:
            keys = transformation.keys()

            check(
                len(keys) == 1,
                AssertionError(
                    f"Transformation had more than one key: {transformation}"
                ),
            )
            transformation_types.add(list(keys)[0])

        transformation_types -= COMMON_TRANSFORMATION_TYPES

        transformation_types_sorted = sorted(transformation_types)
        signature = "_".join(transformation_types_sorted)
        log(f"signature: {signature}")

        # Add to or update the map.
        signature_to_dirs.setdefault(signature, []).append(summary_dir)

    log("\n\nTable:\n")

    for (signature, cases) in sorted(
        signature_to_dirs.items(), key=lambda item: item[0]
    ):
        log(f"{signature}:")
        for case in cases:
            log(f"  {str(case)}")


if __name__ == "__main__":
    main()
