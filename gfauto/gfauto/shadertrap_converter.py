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

"""ShaderTrap shader job converter module.

Converts a GLSL shader job to a ShaderTrap file.
"""

import json
import pathlib

from copy import copy
from dataclasses import dataclass
from enum import Enum
from pathlib import Path

from typing import Dict, List, Match, Optional

from gfauto import binaries_util, shader_job_util, subprocess_util, util
from gfauto.gflogging import log
from gfauto.util import check


@dataclass
class ShaderTrapifySettings:  # pylint: disable=too-many-instance-attributes
    copyright_header_text: Optional[str] = None
    add_generated_comment: bool = False
    add_graphics_fuzz_comment: bool = False
    is_coverage_gap: bool = False
    short_description: Optional[str] = None
    comment_text: Optional[str] = None
    use_default_fence_timeout: bool = False
    extra_commands: Optional[str] = None

    def copy(self: "ShaderTrapifySettings") -> "ShaderTrapifySettings":
        # A shallow copy is adequate.
        return copy(self)


def shadertrap_uniform_buffer_bind(uniform_json: str, prefix: str) -> str:
    """
    Returns ShaderTrap commands for uniform binding.

    Skips the special '$...' keys, if present.

    Aborts if a push constant is found, since these are not supported when targeting GL.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
      },
      "mysampler": {
        "func": "sampler2D",
        "texture": "DEFAULT",
        "binding": 7
      },
      "$compute": { ... will be ignored ... }
    }

    becomes:

      BIND_UNIFORM_BUFFER BUFFER {prefix}_myuniform BINDING 3
      TODO(afd): give command for the texture/sampler

    """
    result = ""
    uniforms = json.loads(uniform_json)
    for name, entry in uniforms.items():
        if name.startswith("$"):
            continue
        if entry["func"] in ["sampler2D", "sampler3D"]:
            assert "texture" in entry.keys()
            raise AssertionError("Textures not yet implemented")
        elif "binding" in entry.keys():
            assert "push_constant" not in entry.keys()
            result += f"BIND_UNIFORM_BUFFER BUFFER {prefix}_{name} BINDING {entry['binding']}\n\n"
        elif "push_constant" in entry.keys():
            raise AssertionError("Push constants are not supported when targeting GL")
        else:
            AssertionError("Uniform should have 'binding' field")

    return result


def uniform_function_to_type(func: str, num_scalar_elements: int) -> str:
    if func == "glUniform1f":
        return "float"
    if func == "glUniform2f":
        return "vec2"
    if func == "glUniform3f":
        return "vec3"
    if func == "glUniform4f":
        return "vec4"
    if func == "glUniform1i":
        return "int"
    if func == "glUniform2i":
        return "ivec2"
    if func == "glUniform3i":
        return "ivec3"
    if func == "glUniform4i":
        return "ivec4"
    if func == "glUniform1ui":
        return "uint"
    if func == "glUniform2ui":
        return "uvec2"
    if func == "glUniform3ui":
        return "uvec3"
    if func == "glUniform4ui":
        return "uvec4"
    if func == "glUniform1fv":
        return f"float[{num_scalar_elements}]"
    if func == "glUniform2fv":
        return f"vec2[{num_scalar_elements / 2}]"
    if func == "glUniform3fv":
        return f"vec3[{num_scalar_elements / 3}]"
    if func == "glUniform4fv":
        return f"vec4[{num_scalar_elements / 4}]"
    if func == "glUniform1iv":
        return f"int[{num_scalar_elements}]"
    if func == "glUniform2iv":
        return f"ivec2[{num_scalar_elements / 2}]"
    if func == "glUniform3iv":
        return f"ivec3[{num_scalar_elements / 3}]"
    if func == "glUniform4iv":
        return f"ivec4[{num_scalar_elements / 4}]"
    if func == "glUniform1uiv":
        return f"uint[{num_scalar_elements}]"
    if func == "glUniform2uiv":
        return f"uvec2[{num_scalar_elements / 2}]"
    if func == "glUniform3uiv":
        return f"uvec3[{num_scalar_elements / 3}]"
    if func == "glUniform4uiv":
        return f"uvec4[{num_scalar_elements / 4}]"
    if func == "glUniformMatrix2fv":
        return f"mat2x2[{num_scalar_elements / 4}]"
    if func == "glUniformMatrix3fv":
        return f"mat3x3[{num_scalar_elements / 9}]"
    if func == "glUniformMatrix4fv":
        return f"mat4x4[{num_scalar_elements / 16}]"
    if func == "glUniformMatrix2x3fv":
        return f"mat2x3[{num_scalar_elements / 6}]"
    if func == "glUniformMatrix3x2fv":
        return f"mat3x2[{num_scalar_elements / 6}]"
    if func == "glUniformMatrix2x4fv":
        return f"mat2x4[{num_scalar_elements / 8}]"
    if func == "glUniformMatrix4x2fv":
        return f"mat4x2[{num_scalar_elements / 8}]"
    if func == "glUniformMatrix3x4fv":
        return f"mat3x4[{num_scalar_elements / 12}]"
    if func == "glUniformMatrix4x3fv":
        return f"mat4x3[{num_scalar_elements / 12}]"
    if func == "sampler2D":
        return "sampler2D"
    raise AssertionError("Unknown function: " + func)


def shadertrap_uniform_buffer_def(uniform_json_contents: str, prefix: str) -> str:
    """
    Returns the string representing ShaderTrap version of uniform definitions.

    Skips the special '$...' keys, if present.

    Aborts if a push constant is found, since these are not supported when targeting GL.

    {
      "myuniform": {
        "func": "glUniform1f",
        "args": [ 42.0 ],
        "binding": 3
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

    # mysampler
    SAMPLER {prefix}_mysampler

    # :param uniform_json_contents:
    # :param prefix: E.g. "reference" or "variant". The buffer names will include this prefix to avoid name
    # clashes.
    # """
    uniform_scalar_element_types: Dict[str, str] = {
        "glUniform1f": "float",
        "glUniform2f": "float",
        "glUniform3f": "float",
        "glUniform4f": "float",
        "glUniform1i": "int",
        "glUniform2i": "int",
        "glUniform3i": "int",
        "glUniform4i": "int",
        "glUniform1ui": "uint",
        "glUniform2ui": "uint",
        "glUniform3ui": "uint",
        "glUniform4ui": "uint",
        "glUniform1fv": "float",
        "glUniform2fv": "float",
        "glUniform3fv": "float",
        "glUniform4fv": "float",
        "glUniform1iv": "int",
        "glUniform2iv": "int",
        "glUniform3iv": "int",
        "glUniform4iv": "int",
        "glUniform1uiv": "uint",
        "glUniform2uiv": "uint",
        "glUniform3uiv": "uint",
        "glUniform4uiv": "uint",
        "glUniformMatrix2fv": "float",
        "glUniformMatrix3fv": "float",
        "glUniformMatrix4fv": "float",
        "glUniformMatrix2x3fv": "float",
        "glUniformMatrix3x2fv": "float",
        "glUniformMatrix2x4fv": "float",
        "glUniformMatrix4x2fv": "float",
        "glUniformMatrix3x4fv": "float",
        "glUniformMatrix4x3fv": "float",
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

        if "binding" in entry.keys():

            if func not in uniform_scalar_element_types.keys():
                raise ValueError("Error: unknown uniform type for function: " + func)

            uniform_type = uniform_scalar_element_types[func]

            if uniform_type == "sampler":
                raise AssertionError("Samplers not yet implemented.")
            else:
                args = entry["args"]
                result += f"# {name}\n"
                result += f"CREATE_BUFFER {prefix}_{name} SIZE_BYTES {len(args) * 4} INIT_VALUES {uniform_type}\n"
                for arg in args:
                    result += f" {arg}"

        else:
            if func == "sampler2D":
                raise AssertionError("Samplers not yet implemented.")
            else:
                args = entry["args"]
                result += f'SET_UNIFORM PROGRAM {prefix}_program NAME "{name}" TYPE {uniform_function_to_type(func, len(args))} VALUES'
                for arg in args:
                    result += f" {arg}"

        result += "\n\n"

    return result


def is_compute_job(input_glsl_job_json_path: pathlib.Path) -> bool:
    comp_files = shader_job_util.get_related_files(
        input_glsl_job_json_path, [shader_job_util.EXT_COMP],
    )
    check(
        len(comp_files) <= 1,
        AssertionError(f"Expected 1 or 0 compute shader files: {comp_files}"),
    )
    return len(comp_files) == 1


class ShaderType(Enum):
    FRAGMENT = "fragment"
    VERTEX = "vertex"
    COMPUTE = "compute"


@dataclass
class Shader:
    shader_type: ShaderType
    shader_source: Optional[str]


@dataclass
class ShaderJob:
    name_prefix: str  # Can be used to create unique ssbo buffer names; uniform names are already unique.
    uniform_definitions: str  # E.g. TODO(afd)
    uniform_bindings: str  # E.g. TODO(afd)


# @dataclass
# class ComputeShaderJob(ShaderJob):
#
#     compute_shader: Shader
#
#     # String containing AmberScript command(s) for defining a buffer containing the initial data for the input/output
#     # buffer that will be used by the compute shader.
#     # This string is a template (use with .format()) where the template argument is the name of buffer.
#     # E.g. BUFFER {} DATA_TYPE vec4<float> DATA 0.0 0.0 END
#     initial_buffer_definition_template: str
#
#     # Same as above, but defines an empty buffer with the same size and type as the initial buffer.
#     # E.g. BUFFER {name} DATA_TYPE vec4<float> SIZE 2 0.0
#     empty_buffer_definition_template: str
#
#     # String specifying the number of groups to run when calling the Amber RUN command. E.g. "7 3 4".
#     num_groups_def: str
#
#     # The binding command for the SSBO buffer.
#     # This string is a template (use with .format()) where the template argument is the name of buffer.
#     # E.g. BIND BUFFER {} AS storage DESCRIPTOR_SET 0 BINDING 0
#     buffer_binding_template: str
#
#
@dataclass
class GraphicsShaderJob(ShaderJob):
    vertex_shader: Shader
    fragment_shader: Shader
    draw_command: str


@dataclass
class ShaderJobFile:
    name_prefix: str  # Uniform names will be prefixed with this name to ensure they are unique. E.g. "reference".
    glsl_shader_job_json: Optional[Path]

    def to_shader_job(self) -> ShaderJob:
        json_contents = util.file_read_text(self.glsl_shader_job_json)

        if is_compute_job(self.glsl_shader_job_json):
            glsl_comp_contents = shader_job_util.get_shader_contents(
                self.glsl_source_json, shader_job_util.EXT_COMP
            )

            # Guaranteed
            assert glsl_comp_contents  # noqa

            # TODO: work out how to support compute.
            assert False
            # return ComputeShaderJob(
            #     self.name_prefix,
            #     shadertrap_uniform_buffer_def(json_contents, self.name_prefix),
            #     shadertrap_uniform_buffer_bind(json_contents, self.name_prefix),
            #     Shader(ShaderType.COMPUTE, glsl_comp_contents,),
            #     shadertrap_comp_buff_def(json_contents),
            #     shadertrap_comp_buff_def(json_contents, make_empty_buffer=True),
            #     shadertrap_comp_num_groups_def(json_contents),
            #     shadertrap_comp_buffer_bind(json_contents),
            # )

        # Get GLSL contents
        glsl_vert_contents = shader_job_util.get_shader_contents(
            self.glsl_shader_job_json, shader_job_util.EXT_VERT
        )
        glsl_frag_contents = shader_job_util.get_shader_contents(
            self.glsl_shader_job_json, shader_job_util.EXT_FRAG
        )

        return GraphicsShaderJob(
            self.name_prefix,
            shadertrap_uniform_buffer_def(json_contents, self.name_prefix),
            shadertrap_uniform_buffer_bind(json_contents, self.name_prefix),
            Shader(ShaderType.VERTEX, glsl_vert_contents,),
            Shader(ShaderType.FRAGMENT, glsl_frag_contents,),
            "todo - draw command",
        )


@dataclass
class ShaderJobBasedShaderTrapTest:
    reference: Optional[ShaderJob]
    variants: List[ShaderJob]


@dataclass
class ShaderJobFileBasedShaderTrapTest:
    reference_glsl_job: Optional[ShaderJobFile]
    variants_glsl_job: List[ShaderJobFile]

    def to_shader_job_based(self) -> ShaderJobBasedShaderTrapTest:
        variants = [
            variant_glsl_job.to_shader_job()
            for variant_glsl_job in self.variants_glsl_job
        ]
        return ShaderJobBasedShaderTrapTest(
            self.reference_glsl_job.to_shader_job()
            if self.reference_glsl_job
            else None,
            variants,
        )


def get_text_as_comment(text: str) -> str:
    lines = text.split("\n")

    # Remove empty lines from start and end.
    while not lines[0]:
        lines.pop(0)
    while not lines[-1]:
        lines.pop()

    lines = [("# " + line).rstrip() for line in lines]
    return "\n".join(lines)


def get_shadertrap_script_header(shadertrapify_settings: ShaderTrapifySettings) -> str:
    # TODO(afd): Support figuring out the API version from the shaders.
    result = "GLES 3.1\n\n"

    if shadertrapify_settings.copyright_header_text:
        result += "\n"
        result += get_text_as_comment(shadertrapify_settings.copyright_header_text)
        result += "\n\n"

    if shadertrapify_settings.add_generated_comment:
        result += "\n# Generated.\n\n"

    if shadertrapify_settings.add_graphics_fuzz_comment:
        if shadertrapify_settings.is_coverage_gap:
            result += (
                "\n# A test for a coverage-gap found by the GraphicsFuzz project.\n"
            )
        else:
            result += "\n# A test for a bug found by the GraphicsFuzz project.\n"

    if shadertrapify_settings.short_description:
        result += f"\n# Short description: {shadertrapify_settings.short_description}\n"

    if shadertrapify_settings.comment_text:
        result += f"\n{get_text_as_comment(shadertrapify_settings.comment_text)}\n"

    return result


def get_passthrough_vertex_shader_source() -> str:
    return """#version 310 es
layout(location = 0) in vec2 pos;
void main(void) {
    gl_Position = vec4(pos, 0.0, 1.0);
}
"""


def get_shadertrap_script_shader_def(shader: Shader, name: str) -> str:
    result = f"DECLARE_SHADER {name} KIND {str(shader.shader_type.value).upper()}\n"
    if shader.shader_source:
        result += shader.shader_source
    else:
        assert shader.shader_type == ShaderType.VERTEX
        result += get_passthrough_vertex_shader_source()
    result += "END\n\n"
    return result


def get_shadertrap_script_compile_shader_command(name: str) -> str:
    return f"COMPILE_SHADER {name + '_compiled'} SHADER {name}\n\n"


def get_shadertrap_script_create_program_command(
    shader_names: List[str], program_name: str
) -> str:
    return f"CREATE_PROGRAM {program_name} SHADERS {' '.join(list(map(lambda x : x + '_compiled', shader_names)))}\n\n"


# noinspection DuplicatedCode
def graphics_shader_job_shadertrap_test_to_shadertrap_script(
    shader_job_shadertrap_test: ShaderJobBasedShaderTrapTest,
    shadertrapify_settings: ShaderTrapifySettings,
) -> str:

    result = get_shadertrap_script_header(shadertrapify_settings)

    jobs = shader_job_shadertrap_test.variants.copy()

    if shader_job_shadertrap_test.reference:
        assert isinstance(
            shader_job_shadertrap_test.reference, GraphicsShaderJob
        )  # noqa
        jobs.insert(0, shader_job_shadertrap_test.reference)

    # Check if any of the shader jobs requires texture generation
    texture_generation = False
    for job in jobs:
        if "default_texture" in job.uniform_bindings:
            texture_generation = True

    # If we need a generated texture, do it before anything else.
    if texture_generation:
        # TODO(afd): support textures
        assert False
    #         result += get_amber_texture_generation_shader_def()
    #         result += get_amber_texture_generation_pipeline_def()
    #         result += "\nCLEAR_COLOR texgen_pipeline 0 0 0 255\n"
    #         result += "CLEAR texgen_pipeline\n"
    #         result += "RUN texgen_pipeline DRAW_RECT POS 0 0  SIZE 256 256\n"

    # Declare buffers necessary for drawing a quad.

    result += "CREATE_BUFFER vertex_buffer SIZE_BYTES 32 INIT_VALUES float\n"
    result += "  -1.0 -1.0\n"
    result += "  -1.0 1.0\n"
    result += "  1.0 -1.0\n"
    result += "  1.0 1.0\n"
    result += "CREATE_BUFFER index_buffer SIZE_BYTES 24 INIT_VALUES uint\n"
    result += "  0 1 2 3 1 2\n\n"

    for job in jobs:
        # Guaranteed, and needed for type checker.
        assert isinstance(job, GraphicsShaderJob)  # noqa

        prefix = job.name_prefix

        vertex_shader_name = f"{prefix}_vertex_shader"
        fragment_shader_name = f"{prefix}_fragment_shader"

        # Define shaders.

        result += get_shadertrap_script_shader_def(
            job.vertex_shader, vertex_shader_name
        )

        result += get_shadertrap_script_shader_def(
            job.fragment_shader, fragment_shader_name
        )

        # Compile shaders.

        result += get_shadertrap_script_compile_shader_command(vertex_shader_name)
        result += get_shadertrap_script_compile_shader_command(fragment_shader_name)

        # Create program.
        graphics_program_name = f"{prefix}_program"
        result += get_shadertrap_script_create_program_command(
            [vertex_shader_name, fragment_shader_name], graphics_program_name
        )

        # Define uniforms for shader job.

        result += "\n"
        result += job.uniform_definitions

        # Run the graphics pipeline to draw a quad.

        result += f"CREATE_RENDERBUFFER {prefix}_renderbuffer WIDTH 256 HEIGHT 256\n\n"
        result += "RUN_GRAPHICS\n"
        result += f"  PROGRAM {graphics_program_name}\n"
        result += "  VERTEX_DATA\n"
        result += "    [ 0 -> BUFFER vertex_buffer OFFSET_BYTES 0 STRIDE_BYTES 8 DIMENSION 2 ]\n"
        result += "  INDEX_DATA index_buffer\n"
        result += "  VERTEX_COUNT 6\n"
        result += "  TOPOLOGY TRIANGLES\n"
        result += "  FRAMEBUFFER_ATTACHMENTS\n"
        result += f"    [ 0 -> {prefix}_renderbuffer ]\n\n"
        result += f'DUMP_RENDERBUFFER RENDERBUFFER {prefix}_renderbuffer FILE "{prefix}.png"\n'

    # Add fuzzy compare of renderbuffers if there's more than one pipeline.

    for pipeline_index in range(1, len(jobs)):
        prefix_0 = jobs[0].name_prefix
        prefix_1 = jobs[pipeline_index].name_prefix
        result += f"ASSERT_SIMILAR_EMD_HISTOGRAM RENDERBUFFERS {prefix_0}_renderbuffer {prefix_1}_renderbuffer TOLERANCE 0.005\n"

    if shadertrapify_settings.extra_commands:
        result += shadertrapify_settings.extra_commands

    return result


# noinspection DuplicatedCode
def compute_shader_job_amber_test_to_amber_script(
    shader_job_amber_test: ShaderJobBasedShaderTrapTest,
    shadertrapify_settings: ShaderTrapifySettings,
) -> str:

    return "Not implemented"


#     jobs = shader_job_amber_test.variants.copy()
#
#     if shader_job_amber_test.reference:
#         assert isinstance(shader_job_amber_test.reference, ComputeShaderJob)  # noqa
#         jobs.insert(0, shader_job_amber_test.reference)
#
#     result = get_amber_script_header(amberfy_settings)
#
#     for job in jobs:
#         # Guaranteed, and needed for type checker.
#         assert isinstance(job, ComputeShaderJob)  # noqa
#
#         prefix = job.name_prefix
#
#         compute_shader_name = f"{prefix}_compute_shader"
#         ssbo_name = f"{prefix}_ssbo"
#
#         # Define shaders.
#
#         result += get_amber_script_shader_def(job.compute_shader, compute_shader_name)
#
#         # Define uniforms for variant shader job.
#
#         result += "\n"
#         result += job.uniform_definitions
#
#         # Define in/out buffer for variant shader job.
#         # Note that |initial_buffer_definition_template| is a string template that takes the buffer name as an argument.
#
#         result += "\n"
#         result += job.initial_buffer_definition_template.format(ssbo_name)
#
#         # Create a pipeline that uses the variant compute shader and binds |variant_ssbo_name|.
#
#         result += f"\nPIPELINE compute {prefix}_pipeline\n"
#         result += f"  ATTACH {compute_shader_name}\n"
#         result += job.uniform_bindings
#         result += job.buffer_binding_template.format(ssbo_name)
#         result += "END\n"
#
#         # Run the pipeline.
#
#         result += f"\nRUN {prefix}_pipeline {job.num_groups_def}\n\n"
#
#     # Add fuzzy compare of result SSBOs if there's more than one pipeline.
#
#     for pipeline_index in range(1, len(jobs)):
#         prefix_0 = jobs[0].name_prefix
#         prefix_1 = jobs[pipeline_index].name_prefix
#         result += f"EXPECT {prefix_0}_ssbo RMSE_BUFFER {prefix_1}_ssbo TOLERANCE 7\n"
#
#     if amberfy_settings.extra_commands:
#         result += amberfy_settings.extra_commands
#
#     return result
#
#
def glsl_shader_job_to_shadertrap_script(
    shader_job_file_shadertrap_test: ShaderJobFileBasedShaderTrapTest,
    output_shadertrap_script_file_path: Path,
    shadertrapify_settings: ShaderTrapifySettings,
) -> Path:

    log(
        f"ShaderTrapify: {[str(variant.glsl_shader_job_json) for variant in shader_job_file_shadertrap_test.variants_glsl_job]} "
        + (
            f"with reference {str(shader_job_file_shadertrap_test.reference_glsl_job.glsl_shader_job_json)} "
            if shader_job_file_shadertrap_test.reference_glsl_job
            else ""
        )
        + f"to {str(output_shadertrap_script_file_path)}"
    )

    shader_job_shadertrap_test = shader_job_file_shadertrap_test.to_shader_job_based()

    if isinstance(shader_job_shadertrap_test.variants[0], GraphicsShaderJob):
        result = graphics_shader_job_shadertrap_test_to_shadertrap_script(
            shader_job_shadertrap_test, shadertrapify_settings
        )

    elif isinstance(shader_job_shadertrap_test.variants[0], ComputeShaderJob):
        result = compute_shader_job_shadertrap_test_to_shadertrap_script(
            shader_job_shadertrap_test, shadertrapify_settings
        )
    else:
        raise AssertionError(
            f"Unknown shader job type: {shader_job_shadertrap_test.variants[0]}"
        )

    util.file_write_text(output_shadertrap_script_file_path, result)
    return output_shadertrap_script_file_path


# def write_shader(
#     shader_asm: str,
#     amber_file: Path,
#     output_dir: Path,
#     shader_type: str,
#     shader_name: str,
#     binaries: binaries_util.BinaryManager,
# ) -> List[Path]:
#
#     files_written: List[Path] = []
#
#     shader_type_to_suffix = {
#         "fragment": shader_job_util.EXT_FRAG,
#         "vertex": shader_job_util.EXT_VERT,
#         "compute": shader_job_util.EXT_COMP,
#     }
#
#     shader_type_suffix = shader_type_to_suffix[shader_type]
#
#     # E.g. ifs-and-whiles.variant_fragment_shader.frag.asm
#     shader_asm_file_path = output_dir / (
#         f"{amber_file.stem}.{shader_name}{shader_type_suffix}{shader_job_util.SUFFIX_ASM_SPIRV}"
#     )
#
#     # E.g. ifs-and-whiles.variant_fragment_shader.frag.spv
#     shader_spirv_file_path = output_dir / (
#         f"{amber_file.stem}.{shader_name}{shader_type_suffix}{shader_job_util.SUFFIX_SPIRV}"
#     )
#
#     # E.g. dEQP-VK.graphicsfuzz.ifs-and-whiles.variant_fragment_shader.spvas
#     # These files can be added to the llpc repo as a shader test.
#     shader_llpc_asm_test_file_path = output_dir / (
#         f"dEQP-VK.graphicsfuzz.{amber_file.stem}.{shader_name}.spvas"
#     )
#
#     util.file_write_text(shader_asm_file_path, shader_asm)
#     files_written.append(shader_asm_file_path)
#
#     spirv_as_path = binaries.get_binary_path_by_name("spirv-as").path
#
#     subprocess_util.run(
#         [
#             str(spirv_as_path),
#             "-o",
#             str(shader_spirv_file_path),
#             str(shader_asm_file_path),
#             "--target-env",
#             "spv1.0",
#         ],
#         verbose=True,
#     )
#
#     files_written.append(shader_spirv_file_path)
#
#     util.file_write_text(
#         shader_llpc_asm_test_file_path,
#         """; BEGIN_SHADERTEST
# ; RUN: amdllpc -verify-ir -spvgen-dir=%spvgendir% -v %gfxip %s | FileCheck -check-prefix=SHADERTEST %s
# ; SHADERTEST-LABEL: {{^// LLPC.*}} SPIRV-to-LLVM translation results
# ; SHADERTEST: AMDLLPC SUCCESS
# ; END_SHADERTEST
# ;
# """
#         + f"; Based on dEQP-VK.graphicsfuzz.{amber_file.stem}\n\n"
#         + shader_asm,
#     )
#     files_written.append(shader_llpc_asm_test_file_path)
#
#     return files_written
#
#
# def extract_shaders_amber_script(
#     amber_file: Path,
#     lines: List[str],
#     output_dir: Path,
#     binaries: binaries_util.BinaryManager,
# ) -> List[Path]:
#     files_written: List[Path] = []
#     i = -1
#     while i < len(lines) - 1:
#         i += 1
#         line = lines[i]
#         if not line.strip().startswith("SHADER"):
#             continue
#         parts = line.strip().split()
#         shader_type = parts[1]
#         shader_name = parts[2]
#         shader_format = parts[3]
#         if shader_format in ("PASSTHROUGH", "GLSL"):
#             continue
#         check(
#             shader_format == "SPIRV-ASM",
#             AssertionError(
#                 f"{str(amber_file)}:{i+1}: unsupported shader format: {shader_format}"
#             ),
#         )
#
#         # Get the target environment string. We do an extra check because this element was introduced more recently.
#         check(
#             len(parts) >= 6 and parts[4] == "TARGET_ENV",
#             AssertionError(f"{str(amber_file)}:{i+1}: missing TARGET_ENV"),
#         )
#
#         shader_target_env = parts[5]
#
#         # We only support target environments that specify a version of SPIR-V.
#         # E.g. TARGET_ENV spv1.5
#         check(
#             shader_target_env.startswith("spv"),
#             AssertionError(f"{str(amber_file)}:{i+1}: TARGET_ENV must start with spv"),
#         )
#
#         spirv_version_from_target_env = util.remove_start(shader_target_env, "spv")
#
#         i += 1
#         shader_asm = ""
#         spirv_version_from_comment = ""
#         while not lines[i].startswith("END"):
#             # We should come across the version comment. E.g.
#             # "; Version: 1.0"
#             if lines[i].startswith("; Version: "):
#                 check(
#                     not spirv_version_from_comment,
#                     AssertionError(
#                         f"{str(amber_file)}:{i+1}: Multiple version comments?"
#                     ),
#                 )
#                 spirv_version_from_comment = lines[i].split()[2]
#                 check(
#                     spirv_version_from_comment == spirv_version_from_target_env,
#                     AssertionError(
#                         f"{str(amber_file)}:{i+1}: TARGET_ENV and version comment mismatch."
#                     ),
#                 )
#
#             shader_asm += lines[i]
#             i += 1
#
#         check(
#             bool(spirv_version_from_comment),
#             AssertionError(
#                 f"{str(amber_file)}:{i+1}: missing version comment in SPIRV-ASM."
#             ),
#         )
#
#         files_written += write_shader(
#             shader_asm=shader_asm,
#             amber_file=amber_file,
#             output_dir=output_dir,
#             shader_type=shader_type,
#             shader_name=shader_name,
#             binaries=binaries,
#         )
#
#     return files_written
#
#
# # E.g. [compute shader spirv]
# VK_SCRIPT_SHADER_REGEX = re.compile(r"\[(compute|fragment|vertex) shader (\w*)\]")
#
#
# def extract_shaders_vkscript(
#     amber_file: Path,
#     lines: List[str],
#     output_dir: Path,
#     binaries: binaries_util.BinaryManager,
# ) -> List[Path]:
#     files_written: List[Path] = []
#     i = -1
#     while i < len(lines) - 1:
#         i += 1
#         line = lines[i]
#         match: Optional[Match[str]] = re.match(VK_SCRIPT_SHADER_REGEX, line.strip())
#         if not match:
#             continue
#         shader_type = match.group(1)
#         shader_language = match.group(2)
#         if shader_language == "passthrough":
#             continue
#         check(
#             shader_language == "spirv",
#             AssertionError(
#                 f"For {str(amber_file)}: unsupported shader language: {shader_language}"
#             ),
#         )
#         i += 1
#         shader_asm = ""
#         while not lines[i].strip().startswith("["):
#             shader_asm += lines[i]
#             i += 1
#         files_written += write_shader(
#             shader_asm=shader_asm,
#             amber_file=amber_file,
#             output_dir=output_dir,
#             shader_type=shader_type,
#             shader_name="shader",
#             binaries=binaries,
#         )
#     return files_written
#
#
# def extract_shaders(
#     amber_file: Path, output_dir: Path, binaries: binaries_util.BinaryManager
# ) -> List[Path]:
#     files_written: List[Path] = []
#     with util.file_open_text(amber_file, "r") as file_handle:
#         lines = file_handle.readlines()
#         if lines[0].startswith("#!amber"):
#             files_written += extract_shaders_amber_script(
#                 amber_file, lines, output_dir, binaries
#             )
#         else:
#             log(f"Skipping VkScript file {str(amber_file)} for now.")
#             files_written += extract_shaders_vkscript(
#                 amber_file, lines, output_dir, binaries
#             )
#
#     return files_written
