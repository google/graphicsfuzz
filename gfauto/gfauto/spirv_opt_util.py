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

"""spirv-opt utility module.

Runs spirv-opt on a SPIR-V shader job to get a new SPIR-V shader job where the shaders have been optimized.
"""

import pathlib
import random
from typing import List, Optional

from gfauto import binaries_util, shader_job_util, subprocess_util, util

SPIRV_OPT_DEFAULT_TIME_LIMIT = 120

OPT_OPTIONS: List[str] = [
    "--ccp",
    "--combine-access-chains",
    "--convert-local-access-chains",
    "--copy-propagate-arrays",
    "--eliminate-dead-branches",
    "--eliminate-dead-code-aggressive",
    "--eliminate-dead-inserts",
    "--eliminate-local-multi-store",
    "--eliminate-local-single-block",
    "--eliminate-local-single-store",
    "--if-conversion",
    "--inline-entry-points-exhaustive",
    "--merge-blocks",
    "--merge-return",
    "--private-to-local",
    "--reduce-load-size",
    "--redundancy-elimination",
    "--scalar-replacement=100",
    "--simplify-instructions",
    "--vector-dce",
]


def random_spirv_opt_args(max_num_args: int = 30) -> List[str]:
    result: List[str] = []
    num_args = random.randint(1, max_num_args)
    for _ in range(0, num_args):
        arg = random.choice(OPT_OPTIONS)
        # --merge-return relies on there not being unreachable code, so we always invoke dead branch
        # elimination before --merge-return.
        if arg in ("--merge-return", "--merge-blocks"):
            result.append("--eliminate-dead-branches")
        result.append(arg)
    return result


def run_spirv_opt_on_spirv_shader(
    input_spirv_file_path: pathlib.Path,
    output_dir_path: pathlib.Path,
    spirv_opt_args: List[str],
    spirv_opt_file_path: Optional[pathlib.Path] = None,
    spirv_opt_no_validate_after_all: bool = False,
    time_limit: int = SPIRV_OPT_DEFAULT_TIME_LIMIT,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> pathlib.Path:

    if not spirv_opt_file_path:
        spirv_opt_file_path = util.tool_on_path(binaries_util.SPIRV_OPT_NAME)

    output_spirv_file_path = output_dir_path / input_spirv_file_path.name

    util.file_mkdirs_parent(output_spirv_file_path)

    cmd = util.HashedCommand()
    cmd.append_program_path(spirv_opt_file_path)
    cmd.append_input_file(input_spirv_file_path)
    cmd.append_str("-o")
    cmd.append_output_file(output_spirv_file_path)
    if not spirv_opt_no_validate_after_all:
        cmd.append_str("--validate-after-all")
    cmd.extend_str(spirv_opt_args)

    if preprocessor_cache and preprocessor_cache.write_cached_output_file(
        cmd, output_spirv_file_path
    ):
        return output_spirv_file_path

    cmd_str = util.prepend_catchsegv_if_available(cmd.cmd)
    subprocess_util.run(cmd_str, timeout=time_limit)

    if preprocessor_cache:
        preprocessor_cache.add_output_to_cache(cmd, output_spirv_file_path)

    return output_spirv_file_path


def run_spirv_opt_on_spirv_shader_job(
    input_spirv_shader_job_json_file_path: pathlib.Path,
    output_spirv_shader_job_json_file_path: pathlib.Path,
    spirv_opt_args: List[str],
    spirv_opt_file_path: Optional[pathlib.Path] = None,
    spirv_opt_no_validate_after_all: bool = False,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> pathlib.Path:

    if not spirv_opt_file_path:
        spirv_opt_file_path = util.tool_on_path(binaries_util.SPIRV_OPT_NAME)

    shader_files = shader_job_util.get_related_files(
        input_spirv_shader_job_json_file_path,
        language_suffix=[shader_job_util.SUFFIX_SPIRV],
    )

    util.copy_file(
        input_spirv_shader_job_json_file_path, output_spirv_shader_job_json_file_path
    )

    for shader_file in shader_files:
        run_spirv_opt_on_spirv_shader(
            shader_file,
            output_spirv_shader_job_json_file_path.parent,
            spirv_opt_args,
            spirv_opt_file_path,
            spirv_opt_no_validate_after_all,
            preprocessor_cache=preprocessor_cache,
        )

    return output_spirv_shader_job_json_file_path
