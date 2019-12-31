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

"""glslangValidator utility module.

Runs glslangValidator on GLSL shader jobs to get SPIR-V shader jobs.
"""

import pathlib
from typing import Optional

from gfauto import binaries_util, shader_job_util, subprocess_util, util

GLSLANG_DEFAULT_TIME_LIMIT = 120


def run_glslang_glsl_shader_to_spirv_shader(
    glsl_shader_path: pathlib.Path,
    output_dir_path: pathlib.Path,
    glslang_validator_file_path: Optional[pathlib.Path] = None,
    time_limit: int = GLSLANG_DEFAULT_TIME_LIMIT,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> pathlib.Path:

    if not glslang_validator_file_path:
        glslang_validator_file_path = util.tool_on_path(
            binaries_util.GLSLANG_VALIDATOR_NAME
        )

    output_spirv_file_path = output_dir_path / (glsl_shader_path.name + ".spv")

    util.file_mkdirs_parent(output_spirv_file_path)

    cmd = util.HashedCommand()
    cmd.append_program_path(glslang_validator_file_path)
    cmd.append_str("-V")
    cmd.append_str("-o")
    cmd.append_output_file(output_spirv_file_path)
    cmd.append_input_file(glsl_shader_path)

    if preprocessor_cache and preprocessor_cache.write_cached_output_file(
        cmd, output_spirv_file_path
    ):
        return output_spirv_file_path

    cmd_str = util.prepend_catchsegv_if_available(cmd.cmd)
    subprocess_util.run(
        cmd_str, timeout=time_limit,
    )

    if preprocessor_cache:
        preprocessor_cache.add_output_to_cache(cmd, output_spirv_file_path)

    return output_spirv_file_path


def run_glslang_glsl_to_spirv_job(
    glsl_shader_job_json_file_path: pathlib.Path,
    spirv_shader_job_json_file_path: pathlib.Path,
    glslang_validator_file_path: Optional[pathlib.Path] = None,
    preprocessor_cache: Optional[util.CommandCache] = None,
) -> pathlib.Path:

    if not glslang_validator_file_path:
        glslang_validator_file_path = util.tool_on_path(
            binaries_util.GLSLANG_VALIDATOR_NAME
        )

    glsl_shader_files = shader_job_util.get_related_files(
        glsl_shader_job_json_file_path
    )

    util.copy_file(glsl_shader_job_json_file_path, spirv_shader_job_json_file_path)

    for glsl_shader_file in glsl_shader_files:
        run_glslang_glsl_shader_to_spirv_shader(
            glsl_shader_file,
            spirv_shader_job_json_file_path.parent,
            glslang_validator_file_path,
            preprocessor_cache=preprocessor_cache,
        )

    return spirv_shader_job_json_file_path
