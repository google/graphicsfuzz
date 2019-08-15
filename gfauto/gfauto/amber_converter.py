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
import abc
import itertools
import json
import pathlib
from copy import copy
from enum import Enum
from pathlib import Path
from typing import List, Optional, Tuple, Dict
import attr

from gfauto import shader_job_util, util
from gfauto.gflogging import log
from gfauto.util import check

AMBER_FENCE_TIMEOUT_MS = 60000


@attr.dataclass
class AmberfySettings:  # pylint: disable=too-many-instance-attributes
    copyright_header_text: Optional[str] = None
    add_generated_comment: bool = False
    add_graphics_fuzz_comment: bool = False
    short_description: Optional[str] = None
    comment_text: Optional[str] = None
    use_default_fence_timeout: bool = False
    extra_commands: Optional[str] = None
    spirv_opt_args: Optional[List[str]] = None
    spirv_opt_hash: Optional[str] = None

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


def amberscript_comp_buff_decl(comp_json: str, make_empty_buffer: bool = False) -> str:
    """
    Returns a string (template) containing AmberScript commands for declaring the initial in/out buffer for a
    compute shader test. Only the "$compute" key is read.

      {
        "myuniform": {
          "func": "glUniform1f",
          "args": [ 42.0 ],
          "binding": 3
        },

        "$compute": {
          "num_groups": [12, 13, 14];
          "buffer": {
            "binding": 123,
            "fields":
            [
              { "type": "int", "data": [ 0 ] },
              { "type": "int", "data": [ 1, 2 ] },
            ]
          }
        }

      }

    becomes:

      BUFFER {} DATA_TYPE int DATA
        0 1 2
      END

    Or, if |make_empty_buffer| is True:

      BUFFER {} DATA_TYPE int SIZE 3 0


    :param comp_json: The shader job JSON as a string.
    :param make_empty_buffer: If true, an "empty" buffer is created that is of the same size and type as the normal
    in/out buffer; the empty buffer can be used to store the contents of the in/out buffer via the Amber COPY command.
    The only difference is the "empty" buffer is initially filled with just one value, which avoids redundantly
    listing hundreds of values that will just be overwritten, and makes it clear(er) for those reading the AmberScript
    file that the initial state of the buffer is unused.
    """

    ssbo_types = {
        "int": "int32",
        "ivec2": "vec2<int32>",
        "ivec3": "vec3<int32>",
        "ivec4": "vec4<int32>",
        "uint": "uint32",
        "float": "float",
        "vec2": "vec2<float>",
        "vec3": "vec3<float>",
        "vec4": "vec4<float>",
    }

    comp = json.loads(comp_json)

    check(
        "$compute" in comp.keys(),
        AssertionError("Cannot find '$compute' key in JSON file"),
    )

    compute_info = comp["$compute"]

    check(
        len(compute_info["buffer"]["fields"]) > 0,
        AssertionError("Compute shader test with empty SSBO"),
    )

    field_types_set = set([field["type"] for field in compute_info["buffer"]["fields"]])

    check(len(field_types_set) == 1, AssertionError("All field types must be the same"))

    ssbo_type = compute_info["buffer"]["fields"][0]["type"]
    if ssbo_type not in ssbo_types.keys():
        raise ValueError(f"Unsupported SSBO datum type: {ssbo_type}")
    ssbo_type_amber = ssbo_types[ssbo_type]
    result = ""

    # E.g. [[0, 0], [5], [1, 2, 3]]
    field_data = [field for field in compute_info["buffer"]["fields"]]

    # E.g. [0, 0, 5, 1, 2, 3]
    # |*| unpacks the list so each element is passed as an argument.
    # |chain| takes a list of iterables.
    field_data_flattened = itertools.chain(*field_data)

    # E.g. ["0", "0", "", "1", "2", "3"]
    field_data_flattened_str = [str(field) for field in field_data_flattened]

    if make_empty_buffer:
        # We just use the first value to initialize every element of the "empty" buffer.
        result += f"BUFFER {{}} DATA_TYPE {ssbo_type_amber} SIZE {len(field_data_flattened_str)} {field_data_flattened_str[0]}\n\n"
    else:
        result += f"BUFFER {{}} DATA_TYPE {ssbo_type_amber} DATA\n"
        result += f" {' '.join(field_data_flattened_str)}\n"
        result += "END\n\n"

    return result


def amberscript_comp_num_groups_def(json_contents: str) -> str:
    shader_job_info = json.loads(json_contents)
    num_groups = shader_job_info["$compute"]["num_groups"]
    num_groups_str = [str(dimension) for dimension in num_groups]
    return " ".join(num_groups_str)


def amberscript_uniform_buffer_bind(uniform_json: str, prefix: str) -> str:
    """
    Returns AmberScript commands for uniform binding.
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

      BIND BUFFER {prefix}_myuniform AS uniform DESCRIPTOR_SET 0 BINDING 3
    """

    result = ""
    uniforms = json.loads(uniform_json)
    for name, entry in uniforms.items():
        if name == "$compute":
            continue
        result += f"  BIND BUFFER {prefix}_{name} AS uniform DESCRIPTOR_SET 0 BINDING {entry['binding']}\n"
    return result


def amberscript_uniform_buffer_def(uniform_json_contents: str, prefix: str) -> str:
    """
    Returns the string representing AmberScript version of uniform declarations.
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

    # uniforms for {prefix}

    # myuniform
    BUFFER {prefix}_myuniform DATA_TYPE float DATA
      42.0
    END

    :param uniform_json_contents:
    :param prefix: E.g. "reference" or "variant". The buffer names will include this prefix to avoid name
    clashes.
    """

    uniform_types: Dict[str, str] = {
        "glUniform1i": "int32",
        "glUniform2i": "vec2<int32>",
        "glUniform3i": "vec3<int32>",
        "glUniform4i": "vec4<int32>",
        "glUniform1f": "float",
        "glUniform2f": "vec2<float>",
        "glUniform3f": "vec3<float>",
        "glUniform4f": "vec4<float>",
        "glUniformMatrix2fv": "mat2x2<float>",
        "glUniformMatrix2x3fv": "mat2x3<float>",
        "glUniformMatrix2x4fv": "mat2x4<float>",
        "glUniformMatrix3x2fv": "mat3x2<float>",
        "glUniformMatrix3fv": "mat3x3<float>",
        "glUniformMatrix3x4fv": "mat3x4<float>",
        "glUniformMatrix4x2fv": "mat4x2<float>",
        "glUniformMatrix4x3fv": "mat4x3<float>",
        "glUniformMatrix4fv": "mat4x4<float>",
    }

    result = f"# uniforms for {prefix}\n\n"

    uniforms = json.loads(uniform_json_contents)
    for name, entry in uniforms.items():

        if name == "$compute":
            continue

        func = entry["func"]
        if func not in uniform_types.keys():
            raise ValueError("Error: unknown uniform type for function: " + func)
        uniform_type = uniform_types[func]

        result += f"# {name}\n"
        result += f"BUFFER {prefix}_{name} DATA_TYPE {uniform_type} DATA\n"
        for arg in entry["args"]:
            result += f" {arg}"
        result += "\n"
        result += "END\n"

    return result


def uniform_json_to_vk_script(uniform_json_contents: str) -> str:
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

    uniforms_text = uniform_json_to_vk_script(shader_job_json_contents)
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
        [shader_job_util.SUFFIX_ASM_SPIRV],
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
    uniforms_text = uniform_json_to_vk_script(shader_job_json_contents)
    if uniforms_text:
        result += "## Uniforms\n"
        result += uniforms_text

    # This also includes the "compute" command that runs the shader.
    result += comp_json_to_amberscript(shader_job_json_contents)

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


class ShaderType(Enum):
    FRAGMENT = "fragment"
    VERTEX = "vertex"
    COMPUTE = "compute"


@attr.dataclass
class Shader:
    shader_type: ShaderType
    shader_spirv_asm: Optional[str]
    shader_source: Optional[str]
    processing_info: str  # E.g. "optimized with spirv-opt -O"


@attr.dataclass
class ShaderJob:
    name_prefix: str  # Can be used to create unique ssbo buffer names; uniform names are already unique.
    uniform_definitions: str  # E.g. BUFFER reference_resolution DATA_TYPE vec2<float> DATA 256.0 256.0 END ...
    uniform_bindings: str  # E.g. BIND BUFFER reference_resolution AS uniform DESCRIPTOR_SET 0 BINDING 2 ...


@attr.dataclass
class ComputeShaderJob(ShaderJob):

    compute_shader: Shader

    # String containing AmberScript command(s) for defining a buffer containing the initial data for the input/output
    # buffer that will be used by the compute shader.
    # This string is a template (use with .format()) where the template argument is the name of buffer.
    # E.g. BUFFER {} DATA_TYPE vec4<float> DATA 0.0 0.0 END
    initial_buffer_definition_template: str

    # Same as above, but defines an empty buffer with the same size and type as the initial buffer.
    # E.g. BUFFER {name} DATA_TYPE vec4<float> SIZE 2 0.0
    empty_buffer_definition_template: str

    # String specifying the number of groups to run when calling the Amber RUN command. E.g. "7 3 4".
    num_groups_def: str


@attr.dataclass
class GraphicsShaderJob(ShaderJob):
    vertex_shader: Shader
    fragment_shader: Shader


@attr.dataclass
class ShaderJobFile:
    name_prefix: str  # Uniform names will be prefixed with this name to ensure they are unique. E.g. "reference".
    asm_spirv_shader_job_json: Path
    glsl_source_json: Optional[Path]
    processing_info: str  # E.g. "optimized with spirv-opt -O"

    def to_shader_job(self) -> ShaderJob:
        json_contents = util.file_read_text(self.asm_spirv_shader_job_json)

        if is_compute_job(self.asm_spirv_shader_job_json):
            glsl_comp_contents = None
            if self.glsl_source_json:
                glsl_comp_contents = shader_job_util.get_shader_contents(
                    self.glsl_source_json, shader_job_util.EXT_COMP
                )
            comp_asm_contents = shader_job_util.get_shader_contents(
                self.asm_spirv_shader_job_json,
                shader_job_util.EXT_COMP,
                shader_job_util.SUFFIX_ASM_SPIRV,
                must_exist=True,
            )

            # Guaranteed
            assert comp_asm_contents  # noqa

            return ComputeShaderJob(
                self.name_prefix,
                amberscript_uniform_buffer_def(json_contents, self.name_prefix),
                amberscript_uniform_buffer_bind(json_contents, self.name_prefix),
                Shader(
                    ShaderType.COMPUTE,
                    comp_asm_contents,
                    glsl_comp_contents,
                    self.processing_info,
                ),
                amberscript_comp_buff_decl(json_contents),
                amberscript_comp_buff_decl(json_contents, make_empty_buffer=True),
                amberscript_comp_num_groups_def(json_contents),
            )

        else:
            # Get GLSL contents
            glsl_vert_contents = None
            glsl_frag_contents = None
            if self.glsl_source_json:
                glsl_vert_contents = shader_job_util.get_shader_contents(
                    self.glsl_source_json, shader_job_util.EXT_VERT
                )
                glsl_frag_contents = shader_job_util.get_shader_contents(
                    self.glsl_source_json, shader_job_util.EXT_FRAG
                )

            # Get spirv asm contents
            vert_contents = shader_job_util.get_shader_contents(
                self.asm_spirv_shader_job_json,
                shader_job_util.EXT_VERT,
                shader_job_util.SUFFIX_ASM_SPIRV,
            )

            frag_contents = shader_job_util.get_shader_contents(
                self.asm_spirv_shader_job_json,
                shader_job_util.EXT_FRAG,
                shader_job_util.SUFFIX_ASM_SPIRV,
                must_exist=True,
            )

            return GraphicsShaderJob(
                self.name_prefix,
                amberscript_uniform_buffer_def(json_contents, self.name_prefix),
                amberscript_uniform_buffer_bind(json_contents, self.name_prefix),
                Shader(
                    ShaderType.VERTEX,
                    vert_contents,
                    glsl_vert_contents,
                    self.processing_info,
                ),
                Shader(
                    ShaderType.FRAGMENT,
                    frag_contents,
                    glsl_frag_contents,
                    self.processing_info,
                ),
            )


@attr.dataclass
class ShaderJobBasedAmberTest:
    reference: Optional[ShaderJob]
    variant: ShaderJob


@attr.dataclass
class ShaderJobFileBasedAmberTest:
    reference_asm_spirv_job: Optional[ShaderJobFile]
    variant_asm_spirv_job: ShaderJobFile

    def to_shader_job_based(self) -> ShaderJobBasedAmberTest:
        return ShaderJobBasedAmberTest(
            self.reference_asm_spirv_job.to_shader_job()
            if self.reference_asm_spirv_job
            else None,
            self.variant_asm_spirv_job.to_shader_job(),
        )


def get_amber_script_header(amberfy_settings: AmberfySettings) -> str:
    result = "#!amber\n"

    if amberfy_settings.copyright_header_text:
        result += "\n"
        result += get_text_as_comment(amberfy_settings.copyright_header_text)
        result += "\n\n"

    if amberfy_settings.add_generated_comment:
        result += "\n# Generated.\n\n"

    if amberfy_settings.add_graphics_fuzz_comment:
        result += "\n# A test for a bug found by GraphicsFuzz.\n"

    if amberfy_settings.short_description:
        result += f"\n# Short description: {amberfy_settings.short_description}\n"

    if amberfy_settings.comment_text:
        result += f"\n{get_text_as_comment(amberfy_settings.comment_text)}\n"

    if amberfy_settings.spirv_opt_args:
        result += "\n"
        result += get_spirv_opt_args_comment(
            amberfy_settings.spirv_opt_args, amberfy_settings.spirv_opt_hash
        )
        result += "\n"

    if not amberfy_settings.use_default_fence_timeout:
        result += f"\nSET ENGINE_DATA fence_timeout_ms {AMBER_FENCE_TIMEOUT_MS}\n"

    return result


def get_amber_script_shader_def(shader: Shader, name: str) -> str:
    result = ""
    if shader.shader_source:
        result += f"\n# {name} is derived from the following GLSL:\n"
        result += get_text_as_comment(shader.shader_source)
    if shader.shader_spirv_asm:
        result += f"\nSHADER {str(shader.shader_type.value)} {name} SPIRV-ASM\n"
        result += shader.shader_spirv_asm
        result += "END\n"
    else:
        result += f"\nSHADER {str(shader.shader_type.value)} {name} PASSTHROUGH\n"

    return result


# noinspection DuplicatedCode
def graphics_shader_job_amber_test_to_amber_script(
    shader_job_amber_test: ShaderJobBasedAmberTest, amberfy_settings: AmberfySettings
) -> str:
    # TODO: handle reference, if present.

    # Guaranteed, and needed for type checker.
    assert isinstance(shader_job_amber_test.variant, GraphicsShaderJob)  # noqa

    result = get_amber_script_header(amberfy_settings)

    variant = shader_job_amber_test.variant

    variant_vertex_shader_name = f"{variant.name_prefix}_vertex_shader"
    variant_fragment_shader_name = f"{variant.name_prefix}_fragment_shader"

    # Define shaders.

    result += "\n"
    result += get_amber_script_shader_def(
        variant.vertex_shader, variant_vertex_shader_name
    )

    result += "\n"
    result += get_amber_script_shader_def(
        variant.fragment_shader, variant_fragment_shader_name
    )

    # Define uniforms for variant shader job.

    result += "\n"
    result += variant.uniform_definitions

    # Currently, this buffer must be called "framebuffer" to be able to dump it from Amber.
    result += "\nBUFFER framebuffer FORMAT B8G8R8A8_UNORM\n"

    # Create a pipeline that uses the variant shaders and writes the output to "framebuffer".

    result += "\nPIPELINE graphics gfz_pipeline\n"
    result += f"  ATTACH {variant_vertex_shader_name}\n"
    result += f"  ATTACH {variant_fragment_shader_name}\n"
    result += "  FRAMEBUFFER_SIZE 256 256\n"
    result += "  BIND BUFFER framebuffer AS color LOCATION 0\n"
    result += variant.uniform_bindings
    result += "END\n"
    result += "CLEAR_COLOR gfz_pipeline 0 0 0 255\n"

    # Run the pipeline.

    result += "\nCLEAR gfz_pipeline\n"
    result += "RUN gfz_pipeline DRAW_RECT POS 0 0 SIZE 256 256\n"

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


# noinspection DuplicatedCode
def compute_shader_job_amber_test_to_amber_script(
    shader_job_amber_test: ShaderJobBasedAmberTest, amberfy_settings: AmberfySettings
) -> str:
    # TODO: handle reference, if present.

    # Guaranteed, and needed for type checker.
    assert isinstance(shader_job_amber_test.variant, ComputeShaderJob)  # noqa

    result = get_amber_script_header(amberfy_settings)

    variant = shader_job_amber_test.variant

    variant_compute_shader_name = f"{variant.name_prefix}_compute_shader"
    variant_ssbo_name = f"{variant.name_prefix}_ssbo"

    # Define shaders.

    result += "\n"
    result += get_amber_script_shader_def(
        variant.compute_shader, variant_compute_shader_name
    )

    # Define uniforms for variant shader job.

    result += "\n"
    result += variant.uniform_definitions

    # Define in/out buffer for variant shader job.
    # Note that |initial_buffer_definition_template| is a string template that takes the buffer name as an argument.

    result += "\n"
    result += variant.initial_buffer_definition_template.format(variant_ssbo_name)

    # Create a pipeline that uses the variant compute shader and binds |variant_ssbo_name|.

    result += "\nPIPELINE compute gfz_pipeline\n"
    result += f"  ATTACH {variant_compute_shader_name}\n"
    result += (
        f"  BIND BUFFER {variant_ssbo_name} AS storage DESCRIPTOR_SET 0 BINDING 0\n"
    )
    result += variant.uniform_bindings
    result += "END\n"

    # Run the pipeline.

    result += f"\nRUN gfz_pipeline {variant.num_groups_def}\n"

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


def spirv_asm_shader_job_to_amber_script(
    shader_job_file_amber_test: ShaderJobFileBasedAmberTest,
    output_amber_script_file_path: Path,
    amberfy_settings: AmberfySettings,
) -> Path:

    log(
        f"Amberfy: {str(shader_job_file_amber_test.variant_asm_spirv_job.asm_spirv_shader_job_json)} "
        + (
            f"with reference {str(shader_job_file_amber_test.reference_asm_spirv_job.asm_spirv_shader_job_json)} "
            if shader_job_file_amber_test.reference_asm_spirv_job
            else ""
        )
        + f"to {str(output_amber_script_file_path)}"
    )

    shader_job_amber_test = shader_job_file_amber_test.to_shader_job_based()

    if isinstance(shader_job_amber_test.variant, GraphicsShaderJob):
        result = graphics_shader_job_amber_test_to_amber_script(
            shader_job_amber_test, amberfy_settings
        )

    elif isinstance(shader_job_amber_test.variant, ComputeShaderJob):
        result = compute_shader_job_amber_test_to_amber_script(
            shader_job_amber_test, amberfy_settings
        )
    else:
        raise AssertionError(
            f"Unknown shader job type: {shader_job_amber_test.variant}"
        )

    util.file_write_text(output_amber_script_file_path, result)
    return output_amber_script_file_path


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

    if is_compute_job(input_asm_spirv_job_json_path):
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
