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

import itertools
import json
import pathlib
import re
from copy import copy
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Dict, List, Match, Optional

from gfauto import binaries_util, shader_job_util, subprocess_util, util
from gfauto.gflogging import log
from gfauto.util import check

AMBER_FENCE_TIMEOUT_MS = 60000


@dataclass
class AmberfySettings:  # pylint: disable=too-many-instance-attributes
    copyright_header_text: Optional[str] = None
    add_generated_comment: bool = False
    add_graphics_fuzz_comment: bool = False
    is_coverage_gap: bool = False
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


def amberscript_comp_buffer_bind(comp_json: str) -> str:
    """
    Returns a string (template) containing an AmberScript command for binding the in/out buffer.

    Only the "$compute" key is read.

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

      BIND BUFFER {} AS storage DESCRIPTOR_SET 0 BINDING 123

    The string template argument (use `format()`) is the name of the SSBO buffer.
    """
    comp = json.loads(comp_json)

    check(
        "$compute" in comp.keys(),
        AssertionError("Cannot find '$compute' key in JSON file"),
    )

    compute_info = comp["$compute"]
    assert "binding" in compute_info["buffer"].keys()
    return f"  BIND BUFFER {{}} AS storage DESCRIPTOR_SET 0 BINDING {compute_info['buffer']['binding']}\n"


def amberscript_comp_buff_def(comp_json: str, make_empty_buffer: bool = False) -> str:
    """
    Returns a string (template) containing AmberScript commands for defining the initial in/out buffer.

    Only the "$compute" key is read.

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

    field_types_set = {field["type"] for field in compute_info["buffer"]["fields"]}

    check(len(field_types_set) == 1, AssertionError("All field types must be the same"))

    ssbo_type = compute_info["buffer"]["fields"][0]["type"]
    if ssbo_type not in ssbo_types.keys():
        raise ValueError(f"Unsupported SSBO datum type: {ssbo_type}")
    ssbo_type_amber = ssbo_types[ssbo_type]

    # E.g. [[0, 0], [5], [1, 2, 3]]
    field_data = [field["data"] for field in compute_info["buffer"]["fields"]]

    # E.g. [0, 0, 5, 1, 2, 3]
    # |*| unpacks the list so each element is passed as an argument.
    # |chain| takes a list of iterables and concatenates them.
    field_data_flattened = itertools.chain(*field_data)

    # E.g. ["0", "0", "5", "1", "2", "3"]
    field_data_flattened_str = [str(field) for field in field_data_flattened]

    result = ""
    if make_empty_buffer:
        # We just use the first value to initialize every element of the "empty" buffer.
        result += f"BUFFER {{}} DATA_TYPE {ssbo_type_amber} SIZE {len(field_data_flattened_str)} {field_data_flattened_str[0]}\n"
    else:
        result += f"BUFFER {{}} DATA_TYPE {ssbo_type_amber} DATA\n"
        result += f" {' '.join(field_data_flattened_str)}\n"
        result += "END\n"

    return result


def amberscript_comp_num_groups_def(json_contents: str) -> str:
    shader_job_info = json.loads(json_contents)
    num_groups = shader_job_info["$compute"]["num_groups"]
    num_groups_str = [str(dimension) for dimension in num_groups]
    return " ".join(num_groups_str)


def amberscript_uniform_buffer_bind(uniform_json: str, prefix: str) -> str:
    """
    Returns AmberScript commands for uniform binding.

    Skips the special '$...' keys, if present.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "mypushconstant": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "push_constant": true
      },
      "mysampler": {
        "func": "sampler2D",
        "texture": "DEFAULT",
        "binding": 7
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

      BIND BUFFER {prefix}_myuniform AS uniform DESCRIPTOR_SET 0 BINDING 3
      BIND BUFFER {prefix}_mypushconstant AS push_constant
      BIND BUFFER default_texture AS combined_image_sampler SAMPLER {prefix}_mysampler DESCRIPTOR_SET 0 BINDING 7

    """
    result = ""
    uniforms = json.loads(uniform_json)
    for name, entry in uniforms.items():
        if name.startswith("$"):
            continue
        if entry["func"] in ["sampler2D", "sampler3D"]:
            assert "texture" in entry.keys()
            if entry["texture"] == "DEFAULT":
                result += f"  BIND BUFFER default_texture AS combined_image_sampler SAMPLER {prefix}_{name} DESCRIPTOR_SET 0 BINDING {entry['binding']}\n"
            else:
                raise AssertionError("Non-default textures not implemented")
        elif "binding" in entry.keys():
            assert "push_constant" not in entry.keys()
            result += f"  BIND BUFFER {prefix}_{name} AS uniform DESCRIPTOR_SET 0 BINDING {entry['binding']}\n"
        elif "push_constant" in entry.keys():
            result += f"  BIND BUFFER {prefix}_{name} AS push_constant\n"
        else:
            AssertionError("Uniform should have 'binding' or 'push_constant' field")

    return result


def amberscript_uniform_buffer_def(uniform_json_contents: str, prefix: str) -> str:
    """
    Returns the string representing AmberScript version of uniform definitions.

    Skips the special '$...' keys, if present.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "mypushconstant": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "push_constant": true
      },
      "mysampler": {
        "func": "sampler2D",
        "texture": "DEFAULT",
        "binding": 5
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

    # uniforms for {prefix}

    # myuniform
    BUFFER {prefix}_myuniform DATA_TYPE float DATA
      42.0
    END

    # mypushconstant
    BUFFER {prefix}_mypushconstant DATA_TYPE float DATA
      42.0
    END

    # mysampler
    SAMPLER {prefix}_mysampler

    :param uniform_json_contents:
    :param prefix: E.g. "reference" or "variant". The buffer names will include this prefix to avoid name
    clashes.
    """
    uniform_types: Dict[str, str] = {
        "glUniform1f": "float",
        "glUniform2f": "vec2<float>",
        "glUniform3f": "vec3<float>",
        "glUniform4f": "vec4<float>",
        "glUniform1i": "int32",
        "glUniform2i": "vec2<int32>",
        "glUniform3i": "vec3<int32>",
        "glUniform4i": "vec4<int32>",
        "glUniform1ui": "uint32",
        "glUniform2ui": "vec2<uint32>",
        "glUniform3ui": "vec3<uint32>",
        "glUniform4ui": "vec4<uint32>",
        "glUniform1fv": "float[]",
        "glUniform2fv": "vec2<float>[]",
        "glUniform3fv": "vec3<float>[]",
        "glUniform4fv": "vec4<float>[]",
        "glUniform1iv": "int32[]",
        "glUniform2iv": "vec2<int32>[]",
        "glUniform3iv": "vec3<int32>[]",
        "glUniform4iv": "vec4<int32>[]",
        "glUniform1uiv": "int32[]",
        "glUniform2uiv": "vec2<uint32>[]",
        "glUniform3uiv": "vec3<uint32>[]",
        "glUniform4uiv": "vec4<uint32>[]",
        "glUniformMatrix2fv": "mat2x2<float>[]",
        "glUniformMatrix3fv": "mat3x3<float>[]",
        "glUniformMatrix4fv": "mat4x4<float>[]",
        "glUniformMatrix2x3fv": "mat2x3<float>[]",
        "glUniformMatrix3x2fv": "mat3x2<float>[]",
        "glUniformMatrix2x4fv": "mat2x4<float>[]",
        "glUniformMatrix4x2fv": "mat4x2<float>[]",
        "glUniformMatrix3x4fv": "mat3x4<float>[]",
        "glUniformMatrix4x3fv": "mat4x3<float>[]",
        "sampler2D": "sampler",
        "sampler3D": "sampler",
    }

    uniforms = json.loads(uniform_json_contents)

    # If there are no uniforms, do not generate anything.
    if not uniforms:
        return ""

    result = f"# uniforms for {prefix}\n"

    result += "\n"

    for name, entry in uniforms.items():

        if name.startswith("$"):
            continue

        func = entry["func"]
        if func not in uniform_types.keys():
            raise ValueError("Error: unknown uniform type for function: " + func)
        uniform_type = uniform_types[func]

        if uniform_type == "sampler":
            result += f"# {name}\n"
            result += f"SAMPLER {prefix}_{name}\n"
            result += "\n"
        else:
            result += f"# {name}\n"
            result += f"BUFFER {prefix}_{name} DATA_TYPE {uniform_type} STD140 DATA\n"
            for arg in entry["args"]:
                result += f" {arg}"
            result += "\n"
            result += "END\n"

    return result


def translate_type_for_amber(type_name: str) -> str:
    if type_name == "bool":
        return "uint"
    return type_name


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


def derive_draw_command(json_contents: str) -> str:
    shader_job_info = json.loads(json_contents)
    if "$grid" in shader_job_info.keys():
        cols = shader_job_info["$grid"]["dimensions"][0]
        rows = shader_job_info["$grid"]["dimensions"][1]
        return f"DRAW_GRID POS 0 0 SIZE 256 256 CELLS {cols} {rows}"
    return "DRAW_RECT POS 0 0 SIZE 256 256"


class ShaderType(Enum):
    FRAGMENT = "fragment"
    VERTEX = "vertex"
    COMPUTE = "compute"


@dataclass
class Shader:
    shader_type: ShaderType
    shader_spirv_asm: Optional[str]
    shader_source: Optional[str]
    processing_info: str  # E.g. "optimized with spirv-opt -O"


@dataclass
class ShaderJob:
    name_prefix: str  # Can be used to create unique ssbo buffer names; uniform names are already unique.
    uniform_definitions: str  # E.g. BUFFER reference_resolution DATA_TYPE vec2<float> DATA 256.0 256.0 END ...
    uniform_bindings: str  # E.g. BIND BUFFER reference_resolution AS uniform DESCRIPTOR_SET 0 BINDING 2 ...


@dataclass
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

    # The binding command for the SSBO buffer.
    # This string is a template (use with .format()) where the template argument is the name of buffer.
    # E.g. BIND BUFFER {} AS storage DESCRIPTOR_SET 0 BINDING 0
    buffer_binding_template: str


@dataclass
class GraphicsShaderJob(ShaderJob):
    vertex_shader: Shader
    fragment_shader: Shader
    draw_command: str


@dataclass
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
                amberscript_comp_buff_def(json_contents),
                amberscript_comp_buff_def(json_contents, make_empty_buffer=True),
                amberscript_comp_num_groups_def(json_contents),
                amberscript_comp_buffer_bind(json_contents),
            )

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

        # Figure out if we want to draw a rectangle or a grid.
        draw_command = derive_draw_command(json_contents)

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
            draw_command,
        )


@dataclass
class ShaderJobBasedAmberTest:
    reference: Optional[ShaderJob]
    variants: List[ShaderJob]


@dataclass
class ShaderJobFileBasedAmberTest:
    reference_asm_spirv_job: Optional[ShaderJobFile]
    variants_asm_spirv_job: List[ShaderJobFile]

    def to_shader_job_based(self) -> ShaderJobBasedAmberTest:
        variants = [
            variant_asm_spirv_job.to_shader_job()
            for variant_asm_spirv_job in self.variants_asm_spirv_job
        ]
        return ShaderJobBasedAmberTest(
            self.reference_asm_spirv_job.to_shader_job()
            if self.reference_asm_spirv_job
            else None,
            variants,
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
        if amberfy_settings.is_coverage_gap:
            result += (
                "\n# A test for a coverage-gap found by the GraphicsFuzz project.\n"
            )
        else:
            result += "\n# A test for a bug found by the GraphicsFuzz project.\n"

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
        groups = re.findall(r"\n; Version: ([\d.]*)", shader.shader_spirv_asm)
        check(
            len(groups) == 1,
            AssertionError(
                f"Could not find version comment in SPIR-V shader {name} (or there were multiple)"
            ),
        )
        spirv_version = groups[0]
        result += f"\nSHADER {str(shader.shader_type.value)} {name} SPIRV-ASM TARGET_ENV spv{spirv_version}\n"
        result += shader.shader_spirv_asm
        result += "END\n"
    else:
        result += f"\nSHADER {str(shader.shader_type.value)} {name} PASSTHROUGH\n"

    return result


def get_amber_texture_generation_shader_def() -> str:
    result = ""
    result += "\nSHADER vertex texgen_vert PASSTHROUGH\n"
    result += "\n"
    result += "SHADER fragment texgen_frag GLSL\n"
    result += "#version 430\n"
    result += "precision highp float;\n"
    result += "\n"
    result += "layout(location = 0) out vec4 _GLF_color;\n"
    result += "\n"
    result += "void main()\n"
    result += "{\n"
    result += " _GLF_color = vec4(\n"
    result += " gl_FragCoord.x * (1.0 / 256.0),\n"
    result += " (int(gl_FragCoord.x) ^ int(gl_FragCoord.y)) * (1.0 / 256.0),\n"
    result += " gl_FragCoord.y * (1.0 / 256.0),\n"
    result += " 1.0);\n"
    result += "}\n"
    result += "END\n"
    return result


def get_amber_texture_generation_pipeline_def() -> str:
    result = ""
    result += "BUFFER default_texture FORMAT B8G8R8A8_UNORM\n"
    result += "\n"
    result += "PIPELINE graphics texgen_pipeline\n"
    result += "  ATTACH texgen_vert\n"
    result += "  ATTACH texgen_frag\n"
    result += "  FRAMEBUFFER_SIZE 256 256\n"
    result += "  BIND BUFFER default_texture AS color LOCATION 0\n"
    result += "END\n"
    return result


# noinspection DuplicatedCode
def graphics_shader_job_amber_test_to_amber_script(
    shader_job_amber_test: ShaderJobBasedAmberTest, amberfy_settings: AmberfySettings
) -> str:

    result = get_amber_script_header(amberfy_settings)

    jobs = shader_job_amber_test.variants.copy()

    if shader_job_amber_test.reference:
        assert isinstance(shader_job_amber_test.reference, GraphicsShaderJob)  # noqa
        jobs.insert(0, shader_job_amber_test.reference)

    # Check if any of the shader jobs requires texture generation
    texture_generation = False
    for job in jobs:
        if "default_texture" in job.uniform_bindings:
            texture_generation = True

    # If we need a generated texture, do it before anything else.
    if texture_generation:
        result += get_amber_texture_generation_shader_def()
        result += get_amber_texture_generation_pipeline_def()
        result += "\nCLEAR_COLOR texgen_pipeline 0 0 0 255\n"
        result += "CLEAR texgen_pipeline\n"
        result += "RUN texgen_pipeline DRAW_RECT POS 0 0  SIZE 256 256\n"

    for job in jobs:
        # Guaranteed, and needed for type checker.
        assert isinstance(job, GraphicsShaderJob)  # noqa

        prefix = job.name_prefix

        vertex_shader_name = f"{prefix}_vertex_shader"
        fragment_shader_name = f"{prefix}_fragment_shader"

        # Define shaders.

        result += get_amber_script_shader_def(job.vertex_shader, vertex_shader_name)

        result += get_amber_script_shader_def(job.fragment_shader, fragment_shader_name)

        # Define uniforms for shader job.

        result += "\n"
        result += job.uniform_definitions

        result += f"\nBUFFER {prefix}_framebuffer FORMAT B8G8R8A8_UNORM\n"

        # Create a pipeline.

        result += f"\nPIPELINE graphics {prefix}_pipeline\n"
        result += f"  ATTACH {vertex_shader_name}\n"
        result += f"  ATTACH {fragment_shader_name}\n"
        result += "  FRAMEBUFFER_SIZE 256 256\n"
        result += f"  BIND BUFFER {prefix}_framebuffer AS color LOCATION 0\n"
        result += job.uniform_bindings
        result += "END\n"
        result += f"CLEAR_COLOR {prefix}_pipeline 0 0 0 255\n"

        # Run the pipeline.

        result += f"\nCLEAR {prefix}_pipeline\n"
        result += f"RUN {prefix}_pipeline {job.draw_command}\n"
        result += "\n"

    # Add fuzzy compare of framebuffers if there's more than one pipeline.

    for pipeline_index in range(1, len(jobs)):
        prefix_0 = jobs[0].name_prefix
        prefix_1 = jobs[pipeline_index].name_prefix
        result += f"EXPECT {prefix_0}_framebuffer EQ_HISTOGRAM_EMD_BUFFER {prefix_1}_framebuffer TOLERANCE 0.005"
        result += "\n"

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


# noinspection DuplicatedCode
def compute_shader_job_amber_test_to_amber_script(
    shader_job_amber_test: ShaderJobBasedAmberTest, amberfy_settings: AmberfySettings
) -> str:

    jobs = shader_job_amber_test.variants.copy()

    if shader_job_amber_test.reference:
        assert isinstance(shader_job_amber_test.reference, ComputeShaderJob)  # noqa
        jobs.insert(0, shader_job_amber_test.reference)

    result = get_amber_script_header(amberfy_settings)

    for job in jobs:
        # Guaranteed, and needed for type checker.
        assert isinstance(job, ComputeShaderJob)  # noqa

        prefix = job.name_prefix

        compute_shader_name = f"{prefix}_compute_shader"
        ssbo_name = f"{prefix}_ssbo"

        # Define shaders.

        result += get_amber_script_shader_def(job.compute_shader, compute_shader_name)

        # Define uniforms for variant shader job.

        result += "\n"
        result += job.uniform_definitions

        # Define in/out buffer for variant shader job.
        # Note that |initial_buffer_definition_template| is a string template that takes the buffer name as an argument.

        result += "\n"
        result += job.initial_buffer_definition_template.format(ssbo_name)

        # Create a pipeline that uses the variant compute shader and binds |variant_ssbo_name|.

        result += f"\nPIPELINE compute {prefix}_pipeline\n"
        result += f"  ATTACH {compute_shader_name}\n"
        result += job.uniform_bindings
        result += job.buffer_binding_template.format(ssbo_name)
        result += "END\n"

        # Run the pipeline.

        result += f"\nRUN {prefix}_pipeline {job.num_groups_def}\n\n"

    # Add fuzzy compare of result SSBOs if there's more than one pipeline.

    for pipeline_index in range(1, len(jobs)):
        prefix_0 = jobs[0].name_prefix
        prefix_1 = jobs[pipeline_index].name_prefix
        result += f"EXPECT {prefix_0}_ssbo RMSE_BUFFER {prefix_1}_ssbo TOLERANCE 7\n"

    if amberfy_settings.extra_commands:
        result += amberfy_settings.extra_commands

    return result


def spirv_asm_shader_job_to_amber_script(
    shader_job_file_amber_test: ShaderJobFileBasedAmberTest,
    output_amber_script_file_path: Path,
    amberfy_settings: AmberfySettings,
) -> Path:

    log(
        f"Amberfy: {[str(variant.asm_spirv_shader_job_json) for variant in shader_job_file_amber_test.variants_asm_spirv_job]} "
        + (
            f"with reference {str(shader_job_file_amber_test.reference_asm_spirv_job.asm_spirv_shader_job_json)} "
            if shader_job_file_amber_test.reference_asm_spirv_job
            else ""
        )
        + f"to {str(output_amber_script_file_path)}"
    )

    shader_job_amber_test = shader_job_file_amber_test.to_shader_job_based()

    if isinstance(shader_job_amber_test.variants[0], GraphicsShaderJob):
        result = graphics_shader_job_amber_test_to_amber_script(
            shader_job_amber_test, amberfy_settings
        )

    elif isinstance(shader_job_amber_test.variants[0], ComputeShaderJob):
        result = compute_shader_job_amber_test_to_amber_script(
            shader_job_amber_test, amberfy_settings
        )
    else:
        raise AssertionError(
            f"Unknown shader job type: {shader_job_amber_test.variants[0]}"
        )

    util.file_write_text(output_amber_script_file_path, result)
    return output_amber_script_file_path


def write_shader(
    shader_asm: str,
    amber_file: Path,
    output_dir: Path,
    shader_type: str,
    shader_name: str,
    binaries: binaries_util.BinaryManager,
) -> List[Path]:

    files_written: List[Path] = []

    shader_type_to_suffix = {
        "fragment": shader_job_util.EXT_FRAG,
        "vertex": shader_job_util.EXT_VERT,
        "compute": shader_job_util.EXT_COMP,
    }

    shader_type_suffix = shader_type_to_suffix[shader_type]

    # E.g. ifs-and-whiles.variant_fragment_shader.frag.asm
    shader_asm_file_path = output_dir / (
        f"{amber_file.stem}.{shader_name}{shader_type_suffix}{shader_job_util.SUFFIX_ASM_SPIRV}"
    )

    # E.g. ifs-and-whiles.variant_fragment_shader.frag.spv
    shader_spirv_file_path = output_dir / (
        f"{amber_file.stem}.{shader_name}{shader_type_suffix}{shader_job_util.SUFFIX_SPIRV}"
    )

    # E.g. dEQP-VK.graphicsfuzz.ifs-and-whiles.variant_fragment_shader.spvas
    # These files can be added to the llpc repo as a shader test.
    shader_llpc_asm_test_file_path = output_dir / (
        f"dEQP-VK.graphicsfuzz.{amber_file.stem}.{shader_name}.spvas"
    )

    util.file_write_text(shader_asm_file_path, shader_asm)
    files_written.append(shader_asm_file_path)

    spirv_as_path = binaries.get_binary_path_by_name("spirv-as").path

    subprocess_util.run(
        [
            str(spirv_as_path),
            "-o",
            str(shader_spirv_file_path),
            str(shader_asm_file_path),
            "--target-env",
            "spv1.0",
        ],
        verbose=True,
    )

    files_written.append(shader_spirv_file_path)

    util.file_write_text(
        shader_llpc_asm_test_file_path,
        """; BEGIN_SHADERTEST
; RUN: amdllpc -verify-ir -spvgen-dir=%spvgendir% -v %gfxip %s | FileCheck -check-prefix=SHADERTEST %s
; SHADERTEST-LABEL: {{^// LLPC.*}} SPIRV-to-LLVM translation results
; SHADERTEST: AMDLLPC SUCCESS
; END_SHADERTEST
;
"""
        + f"; Based on dEQP-VK.graphicsfuzz.{amber_file.stem}\n\n"
        + shader_asm,
    )
    files_written.append(shader_llpc_asm_test_file_path)

    return files_written


def extract_shaders_amber_script(
    amber_file: Path,
    lines: List[str],
    output_dir: Path,
    binaries: binaries_util.BinaryManager,
) -> List[Path]:
    files_written: List[Path] = []
    i = -1
    while i < len(lines) - 1:
        i += 1
        line = lines[i]
        if not line.strip().startswith("SHADER"):
            continue
        parts = line.strip().split()
        shader_type = parts[1]
        shader_name = parts[2]
        shader_format = parts[3]
        if shader_format in ("PASSTHROUGH", "GLSL"):
            continue
        check(
            shader_format == "SPIRV-ASM",
            AssertionError(
                f"{str(amber_file)}:{i+1}: unsupported shader format: {shader_format}"
            ),
        )

        # Get the target environment string. We do an extra check because this element was introduced more recently.
        check(
            len(parts) >= 6 and parts[4] == "TARGET_ENV",
            AssertionError(f"{str(amber_file)}:{i+1}: missing TARGET_ENV"),
        )

        shader_target_env = parts[5]

        # We only support target environments that specify a version of SPIR-V.
        # E.g. TARGET_ENV spv1.5
        check(
            shader_target_env.startswith("spv"),
            AssertionError(f"{str(amber_file)}:{i+1}: TARGET_ENV must start with spv"),
        )

        spirv_version_from_target_env = util.remove_start(shader_target_env, "spv")

        i += 1
        shader_asm = ""
        spirv_version_from_comment = ""
        while not lines[i].startswith("END"):
            # We should come across the version comment. E.g.
            # "; Version: 1.0"
            if lines[i].startswith("; Version: "):
                check(
                    not spirv_version_from_comment,
                    AssertionError(
                        f"{str(amber_file)}:{i+1}: Multiple version comments?"
                    ),
                )
                spirv_version_from_comment = lines[i].split()[2]
                check(
                    spirv_version_from_comment == spirv_version_from_target_env,
                    AssertionError(
                        f"{str(amber_file)}:{i+1}: TARGET_ENV and version comment mismatch."
                    ),
                )

            shader_asm += lines[i]
            i += 1

        check(
            bool(spirv_version_from_comment),
            AssertionError(
                f"{str(amber_file)}:{i+1}: missing version comment in SPIRV-ASM."
            ),
        )

        files_written += write_shader(
            shader_asm=shader_asm,
            amber_file=amber_file,
            output_dir=output_dir,
            shader_type=shader_type,
            shader_name=shader_name,
            binaries=binaries,
        )

    return files_written


# E.g. [compute shader spirv]
VK_SCRIPT_SHADER_REGEX = re.compile(r"\[(compute|fragment|vertex) shader (\w*)\]")


def extract_shaders_vkscript(
    amber_file: Path,
    lines: List[str],
    output_dir: Path,
    binaries: binaries_util.BinaryManager,
) -> List[Path]:
    files_written: List[Path] = []
    i = -1
    while i < len(lines) - 1:
        i += 1
        line = lines[i]
        match: Optional[Match[str]] = re.match(VK_SCRIPT_SHADER_REGEX, line.strip())
        if not match:
            continue
        shader_type = match.group(1)
        shader_language = match.group(2)
        if shader_language == "passthrough":
            continue
        check(
            shader_language == "spirv",
            AssertionError(
                f"For {str(amber_file)}: unsupported shader language: {shader_language}"
            ),
        )
        i += 1
        shader_asm = ""
        while not lines[i].strip().startswith("["):
            shader_asm += lines[i]
            i += 1
        files_written += write_shader(
            shader_asm=shader_asm,
            amber_file=amber_file,
            output_dir=output_dir,
            shader_type=shader_type,
            shader_name="shader",
            binaries=binaries,
        )
    return files_written


def extract_shaders(
    amber_file: Path, output_dir: Path, binaries: binaries_util.BinaryManager
) -> List[Path]:
    files_written: List[Path] = []
    with util.file_open_text(amber_file, "r") as file_handle:
        lines = file_handle.readlines()
        if lines[0].startswith("#!amber"):
            files_written += extract_shaders_amber_script(
                amber_file, lines, output_dir, binaries
            )
        else:
            log(f"Skipping VkScript file {str(amber_file)} for now.")
            files_written += extract_shaders_vkscript(
                amber_file, lines, output_dir, binaries
            )

    return files_written
