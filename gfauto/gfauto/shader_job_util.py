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

import pathlib
from typing import List, Optional, Tuple, Union

from gfauto import util

EXT_FRAG = ".frag"
EXT_VERT = ".vert"
EXT_COMP = ".comp"

EXT_ALL = (EXT_FRAG, EXT_COMP, EXT_VERT)

SUFFIX_GLSL = ""
SUFFIX_SPIRV = ".spv"
SUFFIX_ASM_SPIRV = ".asm"


EXTENSIONS_GLSL_SHADER_JOB_RELATED_FILES = [".frag", ".vert", ".comp"]
EXTENSIONS_SPIRV_SHADER_JOB_RELATED_FILES = [".frag.spv", ".vert.spv", ".comp.spv"]
EXTENSIONS_ASM_SPIRV_SHADER_JOB_RELATED_FILES = [".frag.asm", ".vert.asm", ".comp.asm"]


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


# Get related files.


def get_related_files(
    shader_job_file_path: pathlib.Path,
    extensions: Union[Tuple[str, ...], List[str]] = EXT_ALL,
    language_suffix: str = SUFFIX_GLSL,
) -> List[pathlib.Path]:
    # .frag, .comp, ...
    files = extensions

    # .frag.spv, .comp.spv
    files = [(f + language_suffix) for f in files]

    # variant_001.frag.spv, variant_001.comp.spv (does not exist), ...
    paths = [shader_job_file_path.with_suffix(f) for f in files]

    # variant_001.frag.spv, ...
    paths = [p for p in paths if p.exists()]
    return paths


# Copy files.


def copy(
    shader_job_file_path: pathlib.Path,
    output_shader_job_file_path: pathlib.Path,
    extensions: Union[Tuple[str, ...], List[str]] = EXT_ALL,
    language_suffix: str = SUFFIX_GLSL,
) -> pathlib.Path:

    util.copy_file(shader_job_file_path, output_shader_job_file_path)

    # = [source/variant.frag, source/variant.vert, ...]
    other_paths = get_related_files(shader_job_file_path, extensions, language_suffix)

    # = [variant.frag, variant.vert, ...]
    dest_other_files = [f for f in other_paths]

    # = [dest/variant.frag, dest/variant.vert, ...]
    dest_other_paths = [
        output_shader_job_file_path.with_suffix(f.suffix) for f in dest_other_files
    ]

    for (source, dest) in zip(
        other_paths, dest_other_paths
    ):  # type: (pathlib.Path, pathlib.Path)
        util.copy_file(source, dest)

    return output_shader_job_file_path
