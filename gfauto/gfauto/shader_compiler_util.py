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

"""(Offline) Shader compiler utility module.

Used to run offline shader compilers on all shaders in a shader job.
"""

from pathlib import Path
from typing import List

from gfauto import shader_job_util, subprocess_util, util
from gfauto.device_pb2 import DeviceShaderCompiler
from gfauto.gflogging import log

DEFAULT_TIMEOUT = 600


def run_shader(
    compiler_path: Path, shader_path: Path, args: List[str], output_dir: Path
) -> Path:
    output_file_path = output_dir / (shader_path.name + ".out")
    cmd = [str(compiler_path)]
    cmd += args
    cmd += [str(shader_path), "-o", str(output_file_path)]
    cmd = util.prepend_catchsegv_if_available(cmd)
    subprocess_util.run(cmd, verbose=True, timeout=DEFAULT_TIMEOUT)
    return output_file_path


def run_shader_job(
    shader_compiler_device: DeviceShaderCompiler,
    spirv_shader_job_path: Path,
    output_dir: Path,
) -> List[Path]:
    try:
        compiler_path: Path = util.tool_on_path(shader_compiler_device.binary)
    except util.ToolNotOnPathError:
        # If not found on PATH, then assume it is an absolute path.
        compiler_path = Path(shader_compiler_device.binary)

    log(f"Running {str(compiler_path)} on shader job {str(spirv_shader_job_path)}")

    shader_paths = shader_job_util.get_related_files(
        spirv_shader_job_path, language_suffix=[shader_job_util.SUFFIX_SPIRV]
    )

    log(f"Running {str(compiler_path)} on shaders: {shader_paths}")

    result = []

    for shader_path in shader_paths:
        result.append(
            run_shader(
                compiler_path=compiler_path,
                shader_path=shader_path,
                args=list(shader_compiler_device.args),
                output_dir=output_dir,
            )
        )

    return result
