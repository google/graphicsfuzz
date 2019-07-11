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

"""Amber shader job converter module.

Converts a SPIR-V assembly shader job (all shaders are already disassembled) to an Amber script file.
"""

import json
import pathlib
from copy import copy
from typing import List, Optional

from gfauto import shader_job_util, util
from gfauto.gflogging import log
from gfauto.util import check

AMBER_FENCE_TIMEOUT_MS = 60000


class AmberfySettings:  # pylint: disable=too-many-instance-attributes
    def __init__(
        self,
        copyright_header_text: Optional[str] = None,
        add_generated_comment: bool = False,
        add_graphics_fuzz_comment: bool = False,
        short_description: Optional[str] = None,
        comment_text: Optional[str] = None,
        use_default_fence_timeout: bool = False,
        extra_commands: Optional[str] = None,
        spirv_opt_args: Optional[List[str]] = None,
        spirv_opt_hash: Optional[str] = None,
    ):
        self.copyright_header_text = copyright_header_text
        self.add_generated_comment = add_generated_comment
        self.add_graphics_fuzz_comment = add_graphics_fuzz_comment
        self.short_description = short_description
        self.comment_text = comment_text
        self.use_default_fence_timeout = use_default_fence_timeout
        self.extra_commands = extra_commands
        self.spirv_opt_args = spirv_opt_args
        self.spirv_opt_hash = spirv_opt_hash

    def copy(self: "AmberfySettings") -> "AmberfySettings":
        # A shallow copy is adequate.
        return copy(self)


def get_spirv_opt_args_comment(
    spirv_opt_args: List[str], spirv_opt_hash: Optional[str]
) -> str:
    if not spirv_opt_args:
        return ""
    result = "# Optimized using spirv-opt with the following arguments:\n"
    args = [f"# '{arg}'" for arg in spirv_opt_args]
    result += "\n".join(args)
    if spirv_opt_hash:
        result += f"\n# spirv-opt commit hash: {spirv_opt_hash}"
    result += "\n\n"
    return result


def get_text_as_comment(text: str) -> str:
    lines = text.split("\n")

    # Remove empty lines from start and end.
    while not lines[0]:
        lines.pop(0)
    while not lines[-1]:
        lines.pop()

    lines = [("# " + line).rstrip() for line in lines]
    return "\n".join(lines)


def uniform_json_to_amberscript(uniform_json_contents: str) -> str:
    """
    Returns the string representing VkScript version of uniform declarations.

    Skips the special '$compute' key, if present.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

    # myuniform
    uniform ubo 0:3 float 0 42.0
    """
    uniform_types = {
        "glUniform1i": "int",
        "glUniform2i": "ivec2",
        "glUniform3i": "ivec3",
        "glUniform4i": "ivec4",
        "glUniform1f": "float",
        "glUniform2f": "vec2",
        "glUniform3f": "vec3",
        "glUniform4f": "vec4",
    }

    descriptor_set = 0  # always 0 in our tests
    offset = 0  # We never have uniform offset in our tests

    result = ""
    uniform_json = json.loads(uniform_json_contents)
    for name, entry in uniform_json.items():

        if name == "$compute":
            continue

        func = entry["func"]
        binding = entry["binding"]

        check(
            func in uniform_types.keys(),
            AssertionError(f"unknown uniform type for function{func}"),
        )

        uniform_type = uniform_types[func]

        result += "# " + name + "\n"
        result += f"uniform ubo {descriptor_set}:{binding}"
        result += " " + uniform_type
        result += f" {offset}"
        for arg in entry["args"]:
            result += f" {arg}"
        result += "\n"

    return result


def translate_type_for_amber(type_name: str) -> str:
    if type_name == "bool":
        return "uint"
    return type_name


def comp_json_to_amberscript(shader_json_contents: str) -> str:
    """
    Returns the string representing VkScript version of compute shader setup.

    The compute shader setup is found under the special "$compute" key in the JSON.

      {
        "my_uniform_name": { ... ignored by this function ... },

        "$compute": {
          "num_groups": [12, 13, 14];
          "buffer": {
            "binding": 123,
            "fields": [
              {
                "type": "int",
                "data": [42, 43, 44, 45]
              }
            ]
          }
        }

      }

    becomes:
                           offset
                           |
      ssbo 123 subdata int 0 42 43 44 45

      compute 12 13 14

    """
    shader_json = json.loads(shader_json_contents)
    check(
        "$compute" in shader_json.keys(),
        AssertionError('Cannot find "$compute" key in JSON file'),
    )
    compute_json = shader_json["$compute"]

    result = "## SSBO\n"

    binding = compute_json["buffer"]["binding"]
    offset = 0
    for field_info in compute_json["buffer"]["fields"]:
        result += (
            "ssbo "
            + str(binding)
            + " subdata "
            + translate_type_for_amber(field_info["type"])
            + " "
            + str(offset)
        )
        for datum in field_info["data"]:
            result += " " + str(datum)
            offset += 4
        result += "\n"
    result += "\n"

    result += "compute"
    result += " " + str(compute_json["num_groups"][0])
    result += " " + str(compute_json["num_groups"][1])
    result += " " + str(compute_json["num_groups"][2])
    result += "\n"

    return result


def amberscriptify_image(  # pylint: disable=too-many-branches,too-many-locals
    vert_asm_contents: Optional[str],
    frag_asm_contents: str,
    shader_job_json_contents: str,
    amberfy_settings: AmberfySettings,
    vert_glsl_contents: Optional[str] = None,
    frag_glsl_contents: Optional[str] = None,
    add_red_pixel_probe: bool = False,
) -> str:
    """Generates Amberscript representation of an image test."""
    result = ""

    if amberfy_settings.copyright_header_text:
        result += get_text_as_comment(amberfy_settings.copyright_header_text) + "\n\n"

    if amberfy_settings.add_generated_comment:
        result = "# Generated.\n\n"

    if amberfy_settings.add_graphics_fuzz_comment:
        result += "# A test for a bug found by GraphicsFuzz.\n\n"

    if amberfy_settings.short_description:
        result += f"# Short description: {amberfy_settings.short_description}\n\n"

    if amberfy_settings.comment_text:
        result += get_text_as_comment(amberfy_settings.comment_text) + "\n\n"

    if amberfy_settings.spirv_opt_args:
        result += get_spirv_opt_args_comment(
            amberfy_settings.spirv_opt_args, amberfy_settings.spirv_opt_hash
        )

    if vert_glsl_contents or frag_glsl_contents:
        result += "# Derived from the following GLSL.\n\n"

    if vert_glsl_contents:
        result += "# Vertex shader GLSL:\n"
        result += get_text_as_comment(vert_glsl_contents)
        result += "\n\n"

    if frag_glsl_contents:
        result += "# Fragment shader GLSL:\n"
        result += get_text_as_comment(frag_glsl_contents)
        result += "\n\n"

    result += "[require]\n"
    result += "fbsize 256 256\n"

    if not amberfy_settings.use_default_fence_timeout:
        result += "fence_timeout " + str(AMBER_FENCE_TIMEOUT_MS) + "\n"

    result += "\n"

    if vert_asm_contents:
        result += "[vertex shader spirv]\n"
        result += vert_asm_contents
    else:
        result += "[vertex shader passthrough]"
    result += "\n\n"

    result += "[fragment shader spirv]\n"
    result += frag_asm_contents
    result += "\n\n"

    result += "[test]\n"

    uniforms_text = uniform_json_to_amberscript(shader_job_json_contents)
    if uniforms_text:
        result += "## Uniforms\n"
        result += uniforms_text
        result += "\n"
    result += "draw rect -1 -1 2 2\n"

    if add_red_pixel_probe:
        result += "probe rgba (0, 0) (1, 0, 0, 1)\n"

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


def is_compute_job(input_asm_spirv_job_json_path: pathlib.Path) -> bool:
    comp_files = shader_job_util.get_related_files(
        input_asm_spirv_job_json_path,
        [shader_job_util.EXT_COMP],
        shader_job_util.SUFFIX_ASM_SPIRV,
    )
    check(
        len(comp_files) <= 1,
        AssertionError(f"Expected 1 or 0 compute shader files: {comp_files}"),
    )
    return len(comp_files) == 1


def amberscriptify_comp(
    comp_asm_contents: str,
    shader_job_json_contents: str,
    amberfy_settings: AmberfySettings,
    comp_glsl_contents: Optional[str],
) -> str:
    result = ""

    if amberfy_settings.copyright_header_text:
        result += get_text_as_comment(amberfy_settings.copyright_header_text) + "\n\n"

    if amberfy_settings.add_generated_comment:
        result = "# Generated.\n\n"

    if amberfy_settings.add_graphics_fuzz_comment:
        result += "# A test for a bug found by GraphicsFuzz.\n\n"

    if amberfy_settings.short_description:
        result += f"# Short description: {amberfy_settings.short_description}\n\n"

    if amberfy_settings.comment_text:
        result += get_text_as_comment(amberfy_settings.comment_text) + "\n\n"

    if amberfy_settings.spirv_opt_args:
        result += get_spirv_opt_args_comment(
            amberfy_settings.spirv_opt_args, amberfy_settings.spirv_opt_hash
        )

    if comp_glsl_contents:
        result += "# Derived from the following GLSL.\n\n"
        result += "# Compute shader GLSL:\n"
        result += get_text_as_comment(comp_glsl_contents)
        result += "\n\n"

    if not amberfy_settings.use_default_fence_timeout:
        result += "[require]\n"
        result += "fence_timeout " + str(AMBER_FENCE_TIMEOUT_MS) + "\n\n"

    result += "[compute shader spirv]\n"
    result += comp_asm_contents
    result += "\n\n"

    result += "[test]\n"
    uniforms_text = uniform_json_to_amberscript(shader_job_json_contents)
    if uniforms_text:
        result += "## Uniforms\n"
        result += uniforms_text

    # This also includes the "compute" command that runs the shader.
    result += comp_json_to_amberscript(shader_job_json_contents)

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


def run_spirv_asm_shader_job_to_amber_script(  # pylint: disable=too-many-locals
    input_asm_spirv_job_json_path: pathlib.Path,
    output_amber_script_file_path: pathlib.Path,
    amberfy_settings: AmberfySettings,
    input_glsl_source_json_path: Optional[pathlib.Path] = None,
    add_red_pixel_probe: bool = False,
) -> pathlib.Path:

    log(
        f"Amberfy: {str(input_asm_spirv_job_json_path)} to {str(output_amber_script_file_path)}"
    )

    # Get the JSON contents.
    json_contents = util.file_read_text(input_asm_spirv_job_json_path)

    if is_compute_job(input_asm_spirv_job_json_path):  # pylint:disable=no-else-raise
        glsl_comp_contents = None
        if input_glsl_source_json_path:
            glsl_comp_contents = shader_job_util.get_shader_contents(
                input_glsl_source_json_path, shader_job_util.EXT_COMP
            )
        comp_asm_contents = shader_job_util.get_shader_contents(
            input_asm_spirv_job_json_path,
            shader_job_util.EXT_COMP,
            shader_job_util.SUFFIX_ASM_SPIRV,
            must_exist=True,
        )

        assert comp_asm_contents  # noqa

        result = amberscriptify_comp(
            comp_asm_contents, json_contents, amberfy_settings, glsl_comp_contents
        )

    else:
        # Get GLSL contents
        glsl_vert_contents = None
        glsl_frag_contents = None
        if input_glsl_source_json_path:
            glsl_vert_contents = shader_job_util.get_shader_contents(
                input_glsl_source_json_path, shader_job_util.EXT_VERT
            )
            glsl_frag_contents = shader_job_util.get_shader_contents(
                input_glsl_source_json_path, shader_job_util.EXT_FRAG
            )

        # Get spirv asm contents
        vert_contents = shader_job_util.get_shader_contents(
            input_asm_spirv_job_json_path,
            shader_job_util.EXT_VERT,
            shader_job_util.SUFFIX_ASM_SPIRV,
        )

        frag_contents = shader_job_util.get_shader_contents(
            input_asm_spirv_job_json_path,
            shader_job_util.EXT_FRAG,
            shader_job_util.SUFFIX_ASM_SPIRV,
            must_exist=True,
        )

        # Guaranteed.
        assert frag_contents  # noqa

        result = amberscriptify_image(
            vert_contents,
            frag_contents,
            json_contents,
            amberfy_settings,
            glsl_vert_contents,
            glsl_frag_contents,
            add_red_pixel_probe,
        )

    util.file_write_text(output_amber_script_file_path, result)
    return output_amber_script_file_path
