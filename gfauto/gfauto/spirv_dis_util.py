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

"""spirv-dis utility module.

Converts a SPIR-V shader job to a SPIR-V assembly shader job (the shaders are disassembled).
"""

import pathlib
from typing import Optional

from gfauto import binaries_util, shader_job_util, subprocess_util, util


def run_spirv_dis_on_spirv_shader(
    input_spirv_file_path: pathlib.Path,
    output_dir_path: pathlib.Path,
    spirv_dis_file_path: Optional[pathlib.Path] = None,
) -> pathlib.Path:
    if not spirv_dis_file_path:
        spirv_dis_file_path = util.tool_on_path(binaries_util.SPIRV_DIS_NAME)

    output_spirv_file_path = output_dir_path / (
        util.remove_end(input_spirv_file_path.name, ".spv") + ".asm"
    )

    util.file_mkdirs_parent(output_spirv_file_path)

    subprocess_util.run(
        util.prepend_catchsegv_if_available(
            [
                str(spirv_dis_file_path),
                str(input_spirv_file_path),
                "-o",
                str(output_spirv_file_path),
                "--raw-id",
            ]
        )
    )

    return output_spirv_file_path


def run_spirv_shader_job_to_spirv_asm_shader_job(
    input_spirv_job_json_file_path: pathlib.Path,
    output_spirv_job_json_file_path: pathlib.Path,
    spirv_dis_file_path: Optional[pathlib.Path] = None,
) -> pathlib.Path:

    if not spirv_dis_file_path:
        spirv_dis_file_path = util.tool_on_path(binaries_util.SPIRV_DIS_NAME)

    shader_files = shader_job_util.get_related_files(
        input_spirv_job_json_file_path, language_suffix=[shader_job_util.SUFFIX_SPIRV]
    )

    util.copy_file(input_spirv_job_json_file_path, output_spirv_job_json_file_path)

    for shader_file in shader_files:
        run_spirv_dis_on_spirv_shader(
            shader_file, output_spirv_job_json_file_path.parent, spirv_dis_file_path
        )

    return output_spirv_job_json_file_path
