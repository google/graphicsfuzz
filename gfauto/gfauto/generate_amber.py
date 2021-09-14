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

"""Generates an AmberScript test from a shader job."""

import argparse
import shutil
import sys
from pathlib import Path
from typing import List

from gfauto import (
    binaries_util,
    fuzz_glsl_amber_test,
    fuzz_spirv_amber_test,
    fuzz_test_util,
    gflogging,
    glsl_generate_util,
    settings_util,
    shader_job_util,
    test_util,
    util,
)
from gfauto.device_pb2 import Device, DeviceHost


def main() -> None:  # pylint: disable=too-many-locals;
    parser = argparse.ArgumentParser(
        description="Generates an AmberScript test from a shader job."
    )

    parser.add_argument(
        "shader_job", help="The input .json shader job file.",
    )

    parser.add_argument(
        "--output", help="Output directory.", default="output",
    )

    parser.add_argument(
        "--spirv_opt_args",
        help="Arguments for spirv-opt as a space-separated string, or an empty string to skip running spirv-opt.",
        default="",
    )

    parser.add_argument(
        "--settings",
        help="Path to a settings JSON file for this instance. The file will be generated if needed. ",
        default="settings.json",
    )

    parsed_args = parser.parse_args(sys.argv[1:])

    shader_job: Path = Path(parsed_args.shader_job)
    out_dir: Path = Path(parsed_args.output)
    spirv_opt_args_str: str = parsed_args.spirv_opt_args
    settings_path: Path = Path(parsed_args.settings)

    spirv_opt_args: List[str] = []
    if spirv_opt_args_str:
        spirv_opt_args = spirv_opt_args_str.split(" ")

    settings = settings_util.read_or_create(settings_path)

    binary_manager = binaries_util.get_default_binary_manager(settings)

    staging_dir = out_dir / "staging"

    template_source_dir = staging_dir / "source_template"
    test_dir = staging_dir / "test"

    run_output_dir: Path = out_dir / "run"

    # Remove stale directories.
    if staging_dir.is_dir():
        shutil.rmtree(staging_dir)
    if run_output_dir.is_dir():
        shutil.rmtree(run_output_dir)

    # Create source template and call |make_test|.

    if shader_job_util.get_related_suffixes_that_exist(
        shader_job, language_suffix=[shader_job_util.SUFFIX_SPIRV]
    ):
        # This is a SPIR-V shader job.

        shader_job_util.copy(
            shader_job,
            template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
            language_suffix=shader_job_util.SUFFIXES_SPIRV_FUZZ_INPUT,
        )

        fuzz_spirv_amber_test.make_test(
            template_source_dir,
            test_dir,
            spirv_opt_args=spirv_opt_args,
            binary_manager=binary_manager,
            derived_from=shader_job.stem,
            stable_shader=False,
            common_spirv_args=list(settings.common_spirv_args),
        )

    elif shader_job_util.get_related_suffixes_that_exist(
        shader_job, language_suffix=[shader_job_util.SUFFIX_GLSL]
    ):
        # This is a GLSL shader job.

        # The "graphicsfuzz-tool" tool is designed to be on your PATH so that e.g. ".bat" will be appended on Windows.
        # So we use tool_on_path with a custom PATH to get the actual file we want to execute.
        graphicsfuzz_tool_path = util.tool_on_path(
            "graphicsfuzz-tool",
            str(
                binary_manager.get_binary_path_by_name("graphicsfuzz-tool").path.parent
            ),
        )

        with util.file_open_text(staging_dir / "log.txt", "w") as log_file:
            try:
                gflogging.push_stream_for_logging(log_file)

                # Create the prepared (for Vulkan GLSL) reference.
                glsl_generate_util.run_prepare_reference(
                    graphicsfuzz_tool_path,
                    shader_job,
                    template_source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB,
                    legacy_graphics_fuzz_vulkan_arg=settings.legacy_graphics_fuzz_vulkan_arg,
                )
            finally:
                gflogging.pop_stream_for_logging()

        fuzz_glsl_amber_test.make_test(
            template_source_dir,
            test_dir,
            spirv_opt_args=spirv_opt_args,
            binary_manager=binary_manager,
            derived_from=shader_job.stem,
            stable_shader=False,
            common_spirv_args=list(settings.common_spirv_args),
        )

    else:
        raise AssertionError(
            "Unexpected shader job type; expected GLSL or SPIR-V shaders."
        )

    preprocessor_cache = util.CommandCache()

    fuzz_test_util.run_shader_job(
        source_dir=test_util.get_source_dir(test_dir),
        output_dir=run_output_dir,
        binary_manager=binary_manager,
        device=Device(host=DeviceHost()),
        preprocessor_cache=preprocessor_cache,
        stop_after_amber=True,
    )


if __name__ == "__main__":
    main()
