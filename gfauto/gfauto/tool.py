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

"""Tool module.

Used to convert shader jobs to Amber script tests that are suitable for adding to the VK-GL-CTS project.
"""

from pathlib import Path
from typing import List, Optional

from gfauto import (
    amber_converter,
    artifact_util,
    binaries_util,
    glslang_validator_util,
    shader_job_util,
    spirv_dis_util,
    spirv_opt_util,
    subprocess_util,
    test_util,
    util,
)
from gfauto.util import check

AMBER_COMMAND_PROBE_TOP_LEFT_RED = "probe rgba (0, 0) (1, 0, 0, 1)\n"

AMBER_COMMAND_PROBE_TOP_LEFT_WHITE = "probe rgba (0, 0) (1, 1, 1, 1)\n"


def get_copyright_header_google(year: str) -> str:
    return f"""Copyright {year} Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""


def get_binary_paths_using_artifact_system(
    artifact_path: str
) -> binaries_util.BinaryManager:

    # Deprecated.

    artifact_util.recipes_write_built_in()
    artifact_util.artifact_execute_recipe_if_needed(artifact_path)
    artifact_metadata = artifact_util.artifact_read_metadata(artifact_path)

    return binaries_util.BinaryManager(
        list(artifact_metadata.data.extracted_archive_set.archive_set.binaries)
    )


def amberfy(
    input_json: Path,
    output_amber: Path,
    amberfy_settings: amber_converter.AmberfySettings,
    input_glsl_source_json_path: Optional[Path] = None,
) -> Path:

    shader_job_file_amber_test = amber_converter.ShaderJobFileBasedAmberTest(
        reference_asm_spirv_job=None,
        variant_asm_spirv_job=amber_converter.ShaderJobFile(
            "variant", input_json, input_glsl_source_json_path, ""
        ),
    )
    return amber_converter.spirv_asm_shader_job_to_amber_script(
        shader_job_file_amber_test, output_amber, amberfy_settings
    )


def spirv_dis_shader_job(
    input_json: Path, output_json: Path, binary_paths: binaries_util.BinaryGetter
) -> Path:
    return spirv_dis_util.run_spirv_shader_job_to_spirv_asm_shader_job(
        input_json,
        output_json,
        binary_paths.get_binary_path_by_name(binaries_util.SPIRV_DIS_NAME).path,
    )


def spirv_opt_shader_job(
    input_json: Path,
    spirv_opt_args: List[str],
    output_json: Path,
    binary_paths: binaries_util.BinaryGetter,
) -> Path:
    spirv_opt_binary = binary_paths.get_binary_path_by_name(
        binaries_util.SPIRV_OPT_NAME
    )
    return spirv_opt_util.run_spirv_opt_on_spirv_shader_job(
        input_json,
        output_json,
        spirv_opt_args,
        spirv_opt_binary.path,
        binaries_util.SPIRV_OPT_NO_VALIDATE_AFTER_ALL_TAG
        in spirv_opt_binary.binary.tags,
    )


def glslang_glsl_shader_job_to_spirv(
    input_json: Path, output_json: Path, binary_paths: binaries_util.BinaryGetter
) -> Path:
    return glslang_validator_util.run_glslang_glsl_to_spirv_job(
        input_json,
        output_json,
        binary_paths.get_binary_path_by_name(binaries_util.GLSLANG_VALIDATOR_NAME).path,
    )


def run_spirv_val_on_shader(shader_path: Path, spirv_val_path: Path) -> None:
    subprocess_util.run(
        util.prepend_catchsegv_if_available([str(spirv_val_path), str(shader_path)])
    )


def validate_spirv_shader_job_helper(input_json: Path, spirv_val_path: Path) -> None:
    shader_paths = shader_job_util.get_related_files(
        input_json, shader_job_util.EXT_ALL, [shader_job_util.SUFFIX_SPIRV]
    )
    for shader_path in shader_paths:
        run_spirv_val_on_shader(shader_path, spirv_val_path)


def validate_spirv_shader_job(
    input_json: Path, binary_paths: binaries_util.BinaryGetter
) -> None:
    validate_spirv_shader_job_helper(
        input_json,
        binary_paths.get_binary_path_by_name(binaries_util.SPIRV_VAL_NAME).path,
    )


def glsl_shader_job_to_amber_script(
    input_json: Path,
    output_amber: Path,
    work_dir: Path,
    binary_paths: binaries_util.BinaryGetter,
    amberfy_settings: amber_converter.AmberfySettings,
    spirv_opt_args: Optional[List[str]] = None,
) -> Path:

    result = input_json

    # If GLSL:
    if shader_job_util.get_related_suffixes_that_exist(input_json):

        result = shader_job_util.copy(result, work_dir / "0_glsl" / result.name)

        result = glslang_glsl_shader_job_to_spirv(
            result, work_dir / "1_spirv" / result.name, binary_paths
        )

    # If SPIR-V:
    elif shader_job_util.get_related_suffixes_that_exist(
        input_json, language_suffix=[shader_job_util.SUFFIX_SPIRV]
    ):
        result = shader_job_util.copy(
            result,
            work_dir / "1_spirv" / result.name,
            # Copy all spirv-fuzz related files too:
            language_suffix=shader_job_util.SUFFIXES_SPIRV_FUZZ,
        )
    else:
        raise AssertionError(f"Unrecognized shader job type: {str(input_json)}")

    result_spirv = result

    result = spirv_dis_shader_job(
        result, work_dir / "1_spirv_asm" / result.name, binary_paths
    )

    validate_spirv_shader_job(result_spirv, binary_paths)

    if spirv_opt_args:
        result = result_spirv
        result = spirv_opt_shader_job(
            result, spirv_opt_args, work_dir / "2_spirv_opt" / result.name, binary_paths
        )
        result_spirv = result
        result = spirv_dis_shader_job(
            result, work_dir / "2_spirv_opt_asm" / result.name, binary_paths
        )

        validate_spirv_shader_job(result_spirv, binary_paths)

    result = amberfy(result, output_amber, amberfy_settings, input_json)

    return result


def glsl_shader_job_crash_to_amber_script_for_google_cts(
    input_json: Path,
    output_amber: Path,
    work_dir: Path,
    short_description: str,
    comment_text: str,
    copyright_year: str,
    extra_commands: str,
    binary_paths: Optional[binaries_util.BinaryGetter] = None,
    spirv_opt_args: Optional[List[str]] = None,
    spirv_opt_hash: Optional[str] = None,
    test_metadata_path: Optional[Path] = None,
) -> Path:
    """
    Converts a GLSL shader job to an Amber script suitable for adding to the CTS.

    :param input_json:
    :param output_amber:
    :param work_dir:
    :param short_description: One sentence, 58 characters max., no period, no line breaks.
    :param comment_text: Why the test should pass. Can have line breaks. Ideally make sure lines are not too long.
    :param copyright_year:
    :param extra_commands:
    :param binary_paths:
    :param spirv_opt_args:
    :param spirv_opt_hash:
    :param test_metadata_path:
    :return:
    """
    if not binary_paths:
        check(
            bool(test_metadata_path),
            AssertionError("Must have test_metadata_path or binary_paths"),
        )
        assert test_metadata_path  # noqa
        binary_paths = binaries_util.BinaryManager(
            binaries_util.BinaryManager.get_binary_list_from_test_metadata(
                test_metadata_path
            )
        )

    if not spirv_opt_args:
        check(
            bool(test_metadata_path),
            AssertionError("Must have test_metadata_path or binary_paths"),
        )
        assert test_metadata_path  # noqa
        spirv_opt_args = list(
            test_util.metadata_read_from_path(test_metadata_path).glsl.spirv_opt_args
        )

    if spirv_opt_args and not spirv_opt_hash:
        spirv_opt_hash = binary_paths.get_binary_path_by_name(
            binaries_util.SPIRV_OPT_NAME
        ).binary.version

    return glsl_shader_job_to_amber_script(
        input_json,
        output_amber,
        work_dir,
        binary_paths,
        amber_converter.AmberfySettings(
            copyright_header_text=get_copyright_header_google(copyright_year),
            add_graphics_fuzz_comment=True,
            short_description=short_description,
            comment_text=comment_text,
            use_default_fence_timeout=True,
            extra_commands=extra_commands,
            spirv_opt_args=spirv_opt_args,
            spirv_opt_hash=spirv_opt_hash,
        ),
        spirv_opt_args=spirv_opt_args,
    )
