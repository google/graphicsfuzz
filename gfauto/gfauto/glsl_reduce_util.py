# -*- coding: utf-8 -*-

# Copyright 2021 The GraphicsFuzz Project Authors
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

"""Functionality for running the glsl-reduce tool.

Functions for handling reductions using glsl-reduce, invoked when testing Vulkan or OpenGL (ES) devices via glsl-fuzz.
"""

from pathlib import Path
from typing import List, Optional

from gfauto import (
    binaries_util,
    gflogging,
    subprocess_util,
    test_util,
    util,
)


def run_glsl_reduce(
    source_dir: Path,
    name_of_shader_to_reduce: str,
    output_dir: Path,
    binary_manager: binaries_util.BinaryManager,
    preserve_semantics: bool = False,
    extra_args: Optional[List[str]] = None,
) -> Path:

    input_shader_job = source_dir / name_of_shader_to_reduce / test_util.SHADER_JOB

    glsl_reduce_path = util.tool_on_path(
        "glsl-reduce",
        str(binary_manager.get_binary_path_by_name("graphicsfuzz-tool").path.parent),
    )

    cmd = [
        str(glsl_reduce_path),
        str(input_shader_job),
        "--output",
        str(output_dir),
    ]

    if preserve_semantics:
        cmd.append("--preserve-semantics")

    if extra_args:
        cmd.extend(extra_args)

    cmd.extend(
        [
            # This ensures the arguments that follow are all positional arguments.
            "--",
            "gfauto_interestingness_test",
            str(source_dir),
            # --override_shader_job requires two parameters to follow; the second will be added by glsl-reduce (the shader.json file).
            "--override_shader_job",
            str(name_of_shader_to_reduce),
        ]
    )

    # Log the reduction.
    with util.file_open_text(output_dir / "command.log", "w") as f:
        gflogging.push_stream_for_logging(f)
        try:
            # The reducer can fail, but it will typically output an exception file, so we can ignore the exit code.
            subprocess_util.run(cmd, verbose=True, check_exit_code=False)
        finally:
            gflogging.pop_stream_for_logging()

    return output_dir
