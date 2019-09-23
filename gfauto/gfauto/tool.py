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
from typing import Iterator, List, Optional, Tuple

from attr import dataclass

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

AMBER_COMMAND_EXPECT_RED = (
    "EXPECT variant_framebuffer IDX 0 0 SIZE 256 256 EQ_RGBA 255 0 0 255\n"
)


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
        variants_asm_spirv_job=[
            amber_converter.ShaderJobFile(
                "variant", input_json, input_glsl_source_json_path, ""
            )
        ],
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


@dataclass
class SpirvCombinedShaderJob:
    name: str
    spirv_asm_shader_job: Path
    spirv_shader_job: Path
    glsl_source_shader_job: Optional[Path]

    def __iter__(self) -> Iterator[Path]:
        return iter((self.spirv_asm_shader_job, self.spirv_shader_job))


def compile_shader_job(
    name: str,
    input_json: Path,
    work_dir: Path,
    binary_paths: binaries_util.BinaryGetter,
    spirv_opt_args: Optional[List[str]] = None,
) -> SpirvCombinedShaderJob:

    result = input_json

    glsl_source_shader_job: Optional[Path] = None

    # If GLSL:
    if shader_job_util.get_related_suffixes_that_exist(
        result, language_suffix=(shader_job_util.SUFFIX_GLSL,)
    ):
        glsl_source_shader_job = result

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

    return SpirvCombinedShaderJob(
        name=name,
        spirv_asm_shader_job=result,
        spirv_shader_job=result_spirv,
        glsl_source_shader_job=glsl_source_shader_job,
    )


def glsl_shader_job_to_amber_script(
    input_json: Path,
    output_amber: Path,
    work_dir: Path,
    binary_paths: binaries_util.BinaryGetter,
    amberfy_settings: amber_converter.AmberfySettings,
    spirv_opt_args: Optional[List[str]] = None,
) -> Path:

    spirv_asm_shader_job = compile_shader_job(
        input_json, work_dir, binary_paths, spirv_opt_args
    ).spirv_asm_shader_job

    amber_test_path = amberfy(
        spirv_asm_shader_job, output_amber, amberfy_settings, input_json
    )

    return amber_test_path


def get_compilation_settings(
    binary_paths: Optional[binaries_util.BinaryGetter] = None,
    spirv_opt_args: Optional[List[str]] = None,
    spirv_opt_hash: Optional[str] = None,
    test_metadata_path: Optional[Path] = None,
) -> Tuple[binaries_util.BinaryGetter, Optional[List[str]], Optional[str]]:

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

    if test_metadata_path:
        check(
            bool(not spirv_opt_args),
            AssertionError("Cannot have spirv_opt_args AND test_metadata_path"),
        )
        spirv_opt_args = list(
            test_util.metadata_read_from_path(test_metadata_path).glsl.spirv_opt_args
        )

    if spirv_opt_args and not spirv_opt_hash:
        spirv_opt_hash = binary_paths.get_binary_path_by_name(
            binaries_util.SPIRV_OPT_NAME
        ).binary.version

    return (binary_paths, spirv_opt_args, spirv_opt_hash)


def glsl_shader_job_crash_to_amber_script_for_google_cts(
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
    input_json: Optional[Path] = None,
    source_dir: Optional[Path] = None,
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
    :param source_dir:
    :return:
    """
    if source_dir:
        input_json = source_dir / test_util.VARIANT_DIR / test_util.SHADER_JOB
        test_metadata_path = source_dir / test_util.TEST_METADATA
    else:
        check(bool(input_json), AssertionError("Need input_json or source_dir"))
        assert input_json  # noqa

    # Get compilation settings
    (binary_paths, spirv_opt_args, spirv_opt_hash) = get_compilation_settings(
        binary_paths, spirv_opt_args, spirv_opt_hash, test_metadata_path
    )

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


@dataclass
class NameAndShaderJob:
    name: str
    shader_job: Path


def get_shader_jobs(source_dir: Path) -> List[NameAndShaderJob]:
    shader_job_dir_names = [test_util.REFERENCE_DIR] + sorted(
        variant.name
        for variant in source_dir.glob(test_util.VARIANT_DIR + "*")
        if variant.is_dir()
    )

    shader_jobs = [
        NameAndShaderJob(
            shader_job_dir_name, source_dir / shader_job_dir_name / test_util.SHADER_JOB
        )
        for shader_job_dir_name in shader_job_dir_names
    ]

    return shader_jobs


def glsl_shader_job_wrong_image_to_amber_script_for_google_cts(
    source_dir: Path,
    output_amber: Path,
    work_dir: Path,
    short_description: str,
    comment_text: str,
    copyright_year: str,
    binary_paths: Optional[binaries_util.BinaryGetter] = None,
    spirv_opt_args: Optional[List[str]] = None,
    spirv_opt_hash: Optional[str] = None,
) -> Path:
    """
    Converts a GLSL shader job of a wrong image case to an Amber script suitable for adding to the CTS.

    :param source_dir:
    :param output_amber:
    :param work_dir:
    :param short_description: One sentence, 58 characters max., no period, no line breaks.
    :param comment_text: Why the test should pass. Can have line breaks. Ideally make sure lines are not too long.
    :param copyright_year:
    :param binary_paths:
    :param spirv_opt_args:
    :param spirv_opt_hash:
    :return
    """
    test_metadata_path = source_dir / test_util.TEST_METADATA

    # Populate shader jobs with reference and all available variants
    shader_jobs = get_shader_jobs(source_dir)

    # Get compilation settings
    (binary_paths, spirv_opt_args, spirv_opt_hash) = get_compilation_settings(
        binary_paths, spirv_opt_args, spirv_opt_hash, test_metadata_path
    )

    # Compile all shader jobs
    shader_job_files = [
        amber_converter.ShaderJobFile(
            shader_job,
            compile_shader_job(
                source_dir / shader_job / test_util.SHADER_JOB,
                work_dir / shader_job,
                binary_paths,
                spirv_opt_args=spirv_opt_args,
            ).spirv_asm_shader_job,
            source_dir / shader_job / test_util.SHADER_JOB,
            "",
        )
        for shader_job in shader_jobs
    ]

    return amber_converter.spirv_asm_shader_job_to_amber_script(
        amber_converter.ShaderJobFileBasedAmberTest(
            reference_asm_spirv_job=shader_job_files[0],
            variants_asm_spirv_job=shader_job_files[1:],
        ),
        output_amber,
        amber_converter.AmberfySettings(
            copyright_header_text=get_copyright_header_google(copyright_year),
            add_graphics_fuzz_comment=True,
            short_description=short_description,
            comment_text=comment_text,
            use_default_fence_timeout=True,
            spirv_opt_args=spirv_opt_args,
            spirv_opt_hash=spirv_opt_hash,
        ),
    )
