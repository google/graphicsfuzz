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

"""spirv-fuzz utility module.

Runs spirv-fuzz to generate a variant SPIR-V shader job.
"""

from pathlib import Path
from typing import List, Optional

from gfauto import shader_job_util, subprocess_util, test_util, util
from gfauto.gflogging import log
from gfauto.settings_pb2 import Settings
from gfauto.test_pb2 import Test
from gfauto.util import check

# TODO: Make this 64 bits.

GENERATE_SEED_BITS = 32


def run_replay(
    spirv_fuzz_path: Path,
    variant_shader_spv: Path,
    output_shader_spv: Path,
    other_args: Optional[List[str]] = None,
) -> Path:
    """Replays all transformations except the last to get a similar variant shader."""
    util.check(
        output_shader_spv.suffix == shader_job_util.SUFFIX_SPIRV,
        AssertionError(f"Expected {str(output_shader_spv)} to end with .spv"),
    )

    util.file_mkdirs_parent(output_shader_spv)

    # Copy shader.<STAGE>.facts.
    if variant_shader_spv.with_suffix(shader_job_util.SUFFIX_FACTS).is_file():
        util.copy_file(
            variant_shader_spv.with_suffix(shader_job_util.SUFFIX_FACTS),
            output_shader_spv.with_suffix(shader_job_util.SUFFIX_FACTS),
        )

    # Copy shader.<STAGE>.spv_orig.
    orig_spv = util.copy_file(
        variant_shader_spv.with_suffix(shader_job_util.SUFFIX_SPIRV_ORIG),
        output_shader_spv.with_suffix(shader_job_util.SUFFIX_SPIRV_ORIG),
    )

    transformations = variant_shader_spv.with_suffix(
        shader_job_util.SUFFIX_TRANSFORMATIONS
    )

    cmd = [
        str(spirv_fuzz_path),
        str(orig_spv),
        "-o",
        str(output_shader_spv),
        f"--replay={str(transformations)}",
        "--replay-range=-1",  # replays all transformations except the last
    ]

    if other_args:
        cmd.extend(other_args)

    subprocess_util.run(cmd)

    return output_shader_spv


def run_replay_on_shader_job(
    spirv_fuzz_path: Path,
    variant_shader_job_json: Path,
    output_shader_job_json: Path,
    other_args: Optional[List[str]] = None,
) -> Path:
    """Replays all transformations except the last on all shaders to get a similar variant shader job."""
    util.copy_file(variant_shader_job_json, output_shader_job_json)

    suffixes_that_exist = shader_job_util.get_related_suffixes_that_exist(
        variant_shader_job_json, shader_job_util.EXT_ALL, [shader_job_util.SUFFIX_SPIRV]
    )

    for suffix in suffixes_that_exist:

        run_replay(
            spirv_fuzz_path,
            variant_shader_job_json.with_suffix(suffix),
            output_shader_job_json.with_suffix(suffix),
            other_args=other_args,
        )

    return output_shader_job_json


def run_generate(
    spirv_fuzz_path: Path,
    reference_shader_spv: Path,
    output_shader_spv: Path,
    donors_list_path: Path,
    seed: Optional[str] = None,
    other_args: Optional[List[str]] = None,
) -> Path:

    util.check(
        output_shader_spv.suffix == shader_job_util.SUFFIX_SPIRV,
        AssertionError(f"Expected {str(output_shader_spv)} to end with .spv"),
    )

    util.file_mkdirs_parent(output_shader_spv)
    cmd = [
        str(spirv_fuzz_path),
        str(reference_shader_spv),
        "-o",
        str(output_shader_spv),
        f"--donors={str(donors_list_path)}",
        "--fuzzer-pass-validation",
    ]

    if seed:
        cmd.append(f"--seed={seed}")

    if other_args:
        cmd.extend(other_args)

    subprocess_util.run(cmd)

    # reference.spv -> output.spv_orig

    util.copy_file(
        reference_shader_spv,
        output_shader_spv.with_suffix(shader_job_util.SUFFIX_SPIRV_ORIG),
    )

    # reference.spv.facts -> output.spv.facts

    source_facts_path = reference_shader_spv.with_suffix(shader_job_util.SUFFIX_FACTS)
    dest_facts_path = output_shader_spv.with_suffix(shader_job_util.SUFFIX_FACTS)

    if source_facts_path.exists():
        util.copy_file(source_facts_path, dest_facts_path)

    return output_shader_spv


def run_generate_on_shader_job(
    spirv_fuzz_path: Path,
    reference_shader_json: Path,
    output_shader_json: Path,
    donor_shader_job_paths: Optional[List[Path]] = None,
    seed: Optional[str] = None,
    other_args: Optional[List[str]] = None,
) -> Path:

    if donor_shader_job_paths is None:
        donor_shader_job_paths = []

    util.copy_file(reference_shader_json, output_shader_json)

    suffixes_that_exist = shader_job_util.get_related_suffixes_that_exist(
        reference_shader_json, shader_job_util.EXT_ALL, [shader_job_util.SUFFIX_SPIRV]
    )

    for suffix in suffixes_that_exist:

        # Create a donors list file "donors.{suffix}.txt" containing the file paths to all relevant donor .spv shaders.
        donor_list_contents = ""
        for donor_shader_job_path in donor_shader_job_paths:
            donor_shader_path = donor_shader_job_path.with_suffix(suffix)
            if donor_shader_path.exists():
                donor_list_contents += f"{str(donor_shader_path)}\n"
        donors_list_path = util.file_write_text(
            reference_shader_json.parent / f"donors{suffix}.txt", donor_list_contents
        )

        run_generate(
            spirv_fuzz_path,
            reference_shader_json.with_suffix(suffix),
            output_shader_json.with_suffix(suffix),
            donors_list_path=donors_list_path,
            seed=seed,
            other_args=other_args,
        )

    return output_shader_json


def create_spirv_fuzz_variant_2(
    spirv_fuzz_path: Path, source_dir: Path, settings: Settings,
) -> Optional[Path]:
    """
    Replays all transformations except the last to get variant_2.

    Replays all transformations except the last to get a variant_2 shader job, such that variant <-> variant_2 are
    likely even more similar than reference <-> variant.

    |source_dir| must be a spirv_fuzz test.
    """
    test_metadata: Test = test_util.metadata_read_from_source_dir(source_dir)
    check(test_metadata.HasField("spirv_fuzz"), AssertionError("Not a spirv_fuzz test"))

    variant_shader_job = source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB
    variant_2_shader_job = (
        source_dir / f"{test_util.VARIANT_DIR}_2" / test_util.SHADER_JOB
    )
    if not variant_shader_job.is_file():
        log(
            f"Skip generating variant_2 for {str(source_dir)} because the variant shader job was not found."
        )
        return None

    if variant_2_shader_job.is_file():
        log(
            f"Skip generating variant_2 for {str(source_dir)} because variant_2 shader job already exists."
        )
        return None

    return run_replay_on_shader_job(
        spirv_fuzz_path=spirv_fuzz_path,
        variant_shader_job_json=variant_shader_job,
        output_shader_job_json=variant_2_shader_job,
        other_args=list(settings.common_spirv_args),
    )
