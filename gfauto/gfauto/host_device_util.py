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
import subprocess
from pathlib import Path
from typing import Dict, Optional

from gfauto import fuzz, gflogging, result_util, subprocess_util, util
from gfauto.gflogging import log


def run_amber(
    amber_script_file: Path,
    output_dir: Path,
    dump_image: bool,
    dump_buffer: bool,
    skip_render: bool = False,
    debug_layers: bool = False,
    icd: Optional[Path] = None,
) -> Path:
    with util.file_open_text(
        result_util.get_amber_log_path(output_dir), "w"
    ) as log_file:
        try:
            gflogging.push_stream_for_logging(log_file)

            run_amber_helper(
                amber_script_file,
                output_dir,
                dump_image,
                dump_buffer,
                skip_render,
                debug_layers,
                icd,
            )
        finally:
            gflogging.pop_stream_for_logging()

    return output_dir


def run_amber_helper(
    amber_script_file: Path,
    output_dir: Path,
    dump_image: bool,
    dump_buffer: bool,
    skip_render: bool = False,
    debug_layers: bool = False,
    icd: Optional[Path] = None,
) -> Path:

    # TODO: Use binary paths.

    image_file = output_dir / fuzz.IMAGE_FILE_NAME
    buffer_file = output_dir / fuzz.BUFFER_FILE_NAME

    cmd = [
        str(util.tool_on_path("amber")),
        str(amber_script_file),
        "--log-graphics-calls-time",
    ]

    if not debug_layers:
        cmd.append("-d")

    if skip_render:
        # -ps tells amber to stop after pipeline creation
        cmd.append("-ps")
    else:
        if dump_image:
            cmd.append("-i")
            cmd.append(str(image_file))
        if dump_buffer:
            cmd.append("-b")
            cmd.append(str(buffer_file))
            cmd.append("-B")
            cmd.append("0")

    util.prepend_catchsegv_if_available(cmd)

    status = "UNEXPECTED_ERROR"

    result: Optional[subprocess.CompletedProcess] = None
    env: Optional[Dict[str, str]] = None

    if icd:
        env = {"VK_ICD_FILENAMES": str(icd)}

    try:
        result = subprocess_util.run(
            cmd,
            check_exit_code=False,
            timeout=fuzz.AMBER_RUN_TIME_LIMIT,
            verbose=True,
            env=env,
        )
    except subprocess.TimeoutExpired:
        status = fuzz.STATUS_TIMEOUT

    if result:
        if result.returncode != 0:
            status = fuzz.STATUS_CRASH
        else:
            status = fuzz.STATUS_SUCCESS

    log("\nSTATUS " + status + "\n")

    util.file_write_text(result_util.get_status_path(output_dir), status)

    return output_dir
