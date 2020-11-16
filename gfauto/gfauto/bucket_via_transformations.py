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
import os
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple

from gfauto import util
from gfauto.gflogging import log
from gfauto.util import check, check_dir_exists


COMMON_TRANSFORMATION_TYPES = {
    "addConstantBoolean",
    "addConstantComposite",
    "addConstantNull",
    "addConstantScalar",
    "addDeadBlock",
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
    "replaceIdWithSynonym"
}


def extract_signature(directory):
    return str(directory).split(os.sep)[2]


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

    dir_to_transformations: Dict[str, Set[str]] = {}
    interesting_transformations_to_dirs: List[Tuple[Set[str], List[str]]] = []
    skipped_dirs: Set[str] = set()

    total_dirs = 0
    max_len: int = 0
    signatures = set()
    for summary_dir in summary_dirs:
        if "bad_image" in str(summary_dir):
            continue
        log(f"Checking {summary_dir}")
        transformations_json_file = summary_dir / "reduced_1" / "variant" / "shader.frag.transformations_json"
        if not os.path.isfile(transformations_json_file):
            continue
        total_dirs += 1
        signatures.add(extract_signature(summary_dir))
        transformations_json = util.file_read_text(
            transformations_json_file
        )
        transformations = json.loads(transformations_json)

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
        assert len(transformation_types) > 0
        max_len = max(max_len, len(transformation_types))
        dir_to_transformations[summary_dir] = transformation_types

    phase1_reported = set()
    phase1_distinct_signatures = set()

    print("\nPhase 1:")

    seen_transformations: Set[str] = {'a'}
    for i in range(1, max_len + 1):
        for entry in dir_to_transformations:
            transformations = dir_to_transformations[entry]
            if len(transformations) == i:
                common = seen_transformations.intersection(transformations)
                if len(common) == 0:
                    phase1_reported.add(entry)
                    phase1_distinct_signatures.add(extract_signature(entry))
                    seen_transformations.update(transformations)
                    print(str(entry) + ":\n  " + "_".join(transformations))

    phase2_reported = set()
    phase2_new_distinct_signatures = set()

    print("\nPhase 2:")
    for entry in dir_to_transformations:
        if entry not in phase1_reported:
            transformations = dir_to_transformations[entry]
            not_yet_seen = transformations.difference(seen_transformations)
            if len(not_yet_seen) > 0:
                phase2_reported.add(entry)
                signature = extract_signature(entry)
                if signature not in phase1_distinct_signatures:
                    phase2_new_distinct_signatures.add(signature)
                seen_transformations.update(not_yet_seen)
                print(str(entry) + ":\n  " + "_".join(not_yet_seen) + "\n  " + "_".join(transformations.difference(not_yet_seen)))

    reductions = total_dirs
    distinct_signatures = len(signatures)
    suggestions_1 = len(phase1_reported)
    distinct_1 = len(phase1_distinct_signatures)
    missed_1 = distinct_signatures - distinct_1
    duplicates_1 = suggestions_1 - distinct_1
    suggestions_2 = len(phase2_reported)
    distinct_2 = len(phase2_new_distinct_signatures)
    missed_2 = distinct_signatures - (distinct_1 + distinct_2)
    duplicates_2 = suggestions_2 - distinct_2

    print("Number of reduced bugs: " + str(reductions))
    print("Distinct signatures: " + str(distinct_signatures))
    print()
    print("Num suggestions phase 1: " + str(suggestions_1))
    print("Distinct signatures phase 1: " + str(distinct_1))
    print("Missed signatures phase 1: " + str(missed_1))
    print("Duplicate signatures phase 1: " + str(duplicates_1))
    print()
    print("Num suggestions phase 2: " + str(suggestions_2))
    print("New distinct signatures phase 2: " + str(distinct_2))
    print("Signatures still missed after phase 2: " + str(missed_2))
    print("Duplicate signatures phase 2: " + str(duplicates_2))

    print("Latex table entry:")
    print(' & '.join([str(x) for x in [ reductions, distinct_signatures, suggestions_1, distinct_1, missed_1, duplicates_1, suggestions_2, distinct_2, missed_2, duplicates_2]]))


    """

    signature_to_dirs: Dict[str, List[Path]] = {}
    # For each summary directory, get its signature based on the transformation types remaining
    # and add the info to |signature_to_dirs|.
    for summary_dir in summary_dirs:
        log(f"Checking {summary_dir}")
        transformations_json_file = summary_dir / "reduced_1" / "variant" / "shader.frag.transformations_json"
        if not os.path.isfile(transformations_json_file):
            continue
        transformations_json = util.file_read_text(
            transformations_json_file
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
    """

if __name__ == "__main__":
    main()
