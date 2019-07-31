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

"""Shader job utility module.

Used to copy shader jobs and get the related files of a shader job.
"""
import itertools
import pathlib
from pathlib import Path
from typing import List, Optional, Tuple, Union

from gfauto import util

EXT_FRAG = ".frag"
EXT_VERT = ".vert"
EXT_COMP = ".comp"

EXT_ALL = (EXT_FRAG, EXT_VERT, EXT_COMP)

SUFFIX_GLSL = ""
SUFFIX_SPIRV = ".spv"
SUFFIX_ASM_SPIRV = ".asm"
SUFFIX_FACTS = ".facts"
SUFFIX_TRANSFORMATIONS = ".transformations"
SUFFIX_TRANSFORMATIONS_JSON = ".transformations_json"

SUFFIX_SPIRV_ORIG = ".spv_orig"

SUFFIXES_SPIRV_FUZZ_INPUT = (SUFFIX_SPIRV, SUFFIX_FACTS)

SUFFIXES_SPIRV_FUZZ = (
    SUFFIX_SPIRV,
    SUFFIX_SPIRV_ORIG,
    SUFFIX_FACTS,
    SUFFIX_TRANSFORMATIONS,
    SUFFIX_TRANSFORMATIONS_JSON,
)


def get_shader_contents(
    shader_job_file_path: pathlib.Path,
    extension: str,
    language_suffix: str = SUFFIX_GLSL,
    must_exist: bool = False,
) -> Optional[str]:
    shader_file = shader_job_file_path.with_suffix(extension + language_suffix)
    if shader_file.exists():
        return util.file_read_text(shader_file)
    if must_exist:
        raise AssertionError(f"could not read {shader_file}")

    return None


def get_related_suffixes_that_exist(
    shader_job_file_path: Path,
    extensions: Union[Tuple[str, ...], List[str]] = EXT_ALL,
    language_suffix: Union[Tuple[str, ...], List[str]] = (SUFFIX_GLSL,),
) -> List[str]:
    # Cross product of extensions and suffixes as tuples.
    # (".frag", ".comp", ...) x (".facts", ".spv")
    suffixes_as_tuples = itertools.product(extensions, language_suffix)

    # Same as above but as str. E.g. ".frag.facts", ".frag.spv"
    suffixes = [file_parts[0] + file_parts[1] for file_parts in suffixes_as_tuples]

    # Filter
    suffixes_that_exist = [
        s for s in suffixes if shader_job_file_path.with_suffix(s).is_file()
    ]

    return suffixes_that_exist


def get_related_files(
    shader_job_file_path: pathlib.Path,
    extensions: Union[Tuple[str, ...], List[str]] = EXT_ALL,
    language_suffix: Union[Tuple[str, ...], List[str]] = (SUFFIX_GLSL,),
) -> List[pathlib.Path]:

    suffixes_that_exist = get_related_suffixes_that_exist(
        shader_job_file_path, extensions, language_suffix
    )

    # variant_001.frag.spv, ...
    paths = [shader_job_file_path.with_suffix(s) for s in suffixes_that_exist]

    return paths


def copy(
    shader_job_file_path: pathlib.Path,
    output_shader_job_file_path: pathlib.Path,
    extensions: Union[Tuple[str, ...], List[str]] = EXT_ALL,
    language_suffix: Union[Tuple[str, ...], List[str]] = (SUFFIX_GLSL,),
) -> pathlib.Path:

    util.copy_file(shader_job_file_path, output_shader_job_file_path)

    suffixes_that_exist = get_related_suffixes_that_exist(
        shader_job_file_path, extensions, language_suffix
    )

    for suffix in suffixes_that_exist:
        util.copy_file(
            shader_job_file_path.with_suffix(suffix),
            output_shader_job_file_path.with_suffix(suffix),
        )

    return output_shader_job_file_path
