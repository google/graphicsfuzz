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

from gfauto import shader_job_util, subprocess_util, util

# TODO: Make this 64 bits.

GENERATE_SEED_BITS = 32


def run_generate(
    spirv_fuzz_path: Path,
    reference_shader_spv: Path,
    output_shader_spv: Path,
    seed: Optional[str] = None,
    other_args: Optional[List[str]] = None,
) -> Path:

    util.check(
        output_shader_spv.suffix == shader_job_util.SUFFIX_SPIRV,
        AssertionError(f"Expected {str(output_shader_spv)} to end with .spv"),
    )

    cmd = [
        str(spirv_fuzz_path),
        str(reference_shader_spv),
        "-o",
        str(output_shader_spv),
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
    seed: Optional[str] = None,
    other_args: Optional[List[str]] = None,
) -> Path:

    util.copy_file(reference_shader_json, output_shader_json)

    suffixes_that_exist = shader_job_util.get_related_suffixes_that_exist(
        reference_shader_json, shader_job_util.EXT_ALL, [shader_job_util.SUFFIX_SPIRV]
    )

    for suffix in suffixes_that_exist:
        run_generate(
            spirv_fuzz_path,
            reference_shader_json.with_suffix(suffix),
            output_shader_json.with_suffix(suffix),
            seed,
            other_args,
        )

    return output_shader_json
