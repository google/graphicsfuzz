#!/usr/bin/env python3

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

import argparse
import json
from typing import List

import gfuzz_common

# Note: We define a 'shader job' as the JSON file of a GraphicsFuzz shader.
# Each shader job has a corresponding shader file that has the same base name
# as the shader job - for example, a shader job file named 'variant_005.json' will
# have a corresponding shader file 'variant_005.frag' or 'variant_005.comp' in
# the same directory.

# Piglit test section headers
REQUIRE_HEADER = '[require]'
VERTEX_HEADER = '[vertex shader passthrough]'
FRAGMENT_HEADER = '[fragment shader]'
TEST_HEADER = '[test]'

# Draw command for piglit to draw the shader's output. Required in the test header.
DRAW_COMMAND = 'draw rect -1 -1 2 2'
# Command to clear the screen to black before drawing, for the test header.
CLEAR_COMMAND = 'clear color 0.0 0.0 0.0 1.0'

# Strings used to specify the GL version to use in a piglit test's
# [require] header.
GLES_VERSION_STRING = 'GL ES >= '
GLSL_VERSION_STRING = 'GLSL >= '
GLSLES_VERSION_STRING = 'GLSL ES >= '

# GLSL preprocessor version flag.
SHADER_VERSION_FLAG = '#version'
# GLES specifier in version string.
ES_SPECIFIER = ' es'
# GLSL version headers specify a version without a decimal point, when piglit takes a version
# string with a decimal point. The easiest way of getting this is by dividing by 100.
SHADER_VERSION_FACTOR = 100
# Uniform types to be found in the JSON.
UNIFORM_TYPES = {
    'glUniform1i': 'int',
    'glUniform2i': 'ivec2',
    'glUniform3i': 'ivec3',
    'glUniform4i': 'ivec4',
    'glUniform1ui': 'uint',
    'glUniform2ui': 'uvec2',
    'glUniform3ui': 'uvec3',
    'glUniform4ui': 'uvec4',
    'glUniform1f': 'float',
    'glUniform2f': 'vec2',
    'glUniform3f': 'vec3',
    'glUniform4f': 'vec4',
    'glUniformMatrix2fv': 'mat2',
    'glUniformMatrix3fv': 'mat3',
    'glUniformMatrix4fv': 'mat4',
    'glUniformMatrix2x3fv': 'mat2x3',
    'glUniformMatrix3x2fv': 'mat3x2',
    'glUniformMatrix2x4fv': 'mat2x4',
    'glUniformMatrix4x2fv': 'mat4x2',
    'glUniformMatrix3x4fv': 'mat3x4',
    'glUniformMatrix4x3fv': 'mat4x3'
}
UNIFORM_DEC = 'uniform'


def make_shader_test_string(shader_job: str, nodraw: bool) -> str:
    """
    Makes a piglit shader_test from a shader job and shader.
    :param shader_job: The path to the shader job file.
    :param nodraw: determines if the draw command is added to draw the shader.
    :return: the shader_test
    """
    shader_job_json_parsed = get_json_properties(shader_job)

    with gfuzz_common.open_helper(get_shader_from_job(shader_job), 'r') as shader:
        shader_file_string = shader.read()

    shader_lines = shader_file_string.split('\n')
    shader_test_string = ''
    # The version header always has to be on the first line of the shader.
    shader_version_header = shader_lines[0]

    shader_test_string += make_require_header(shader_version_header) + '\n'
    shader_test_string += make_vertex_shader_header() + '\n'
    shader_test_string += make_fragment_shader_header(shader_file_string) + '\n'
    shader_test_string += make_test_header(shader_job_json_parsed, nodraw)

    return shader_test_string


def make_require_header(shader_version_header: str) -> str:
    """
    Creates the piglit [require] header as well as the required GL and GLSL version strings.
    Note: GLSL version strings are formatted as '#version ### (es)', where ### is a
    specific GLSL version multiplied by 100, and (es) is an optional specifier that
    determines whether to use GLES or not.
    :param shader_version_header: the version string in the GLSL file.
    :return: the shader_test require header string.
    """
    require_header = REQUIRE_HEADER + '\n'
    # Piglit requires a version number with 1 digit of precision for the GL version, and
    # 2 digits of precision for the GLSL version.
    try:
        shader_version = shader_version_header.split(' ')[1]
    except IndexError:
        raise IOError('Malformed shader - invalid GLSL version string.')
    if not shader_version.isdigit():
        raise IOError('Malformed shader - invalid GLSL version string.')
    # Piglit requires GL version to be specified explicitly if ES is in use.
    if ES_SPECIFIER in shader_version_header:
        require_header += GLES_VERSION_STRING + \
            format(float(shader_version) / SHADER_VERSION_FACTOR, '.1f') + '\n'
        require_header += GLSLES_VERSION_STRING
    else:
        require_header += GLSL_VERSION_STRING
    require_header += format(float(shader_version) / SHADER_VERSION_FACTOR, '.2f') + '\n'
    return require_header


def make_vertex_shader_header() -> str:
    """
    Creates the piglit [vertex shader] header. Currently uses passthrough, but this function can
    be modified later if we have a need for an explicit vertex shader.
    :return: the shader_test vertex header string.
    """
    return VERTEX_HEADER + '\n'


def make_fragment_shader_header(fragment_shader: str) -> str:
    """
    Creates the piglit [fragment shader] header.
    :param fragment_shader: the fragment shader code.
    :return: the shader_test fragment header string.
    """
    frag_header = FRAGMENT_HEADER + '\n'
    frag_header += fragment_shader
    return frag_header


def make_test_header(shader_job_json_parsed: dict, nodraw: bool) -> str:
    """
    Creates the [test] header. Loads uniforms based on the uniforms found in the JSON file.
    :param shader_job_json_parsed: the parsed JSON properties.
    :param nodraw: determines if the draw command is added to draw the shader.
    :return: the shader_test test header string.
    """
    test_header = TEST_HEADER + '\n'
    for uniform_name, value in shader_job_json_parsed.items():
        test_header += UNIFORM_DEC + ' {type} {uniform_name} {args}\n'.format(
            type=get_uniform_type_from_gl_func(value['func']),
            uniform_name=uniform_name,
            args=' '.join([str(arg) for arg in value['args']])
        )
    if not nodraw:
        test_header += CLEAR_COMMAND + '\n'
        test_header += DRAW_COMMAND
    return test_header


def get_uniform_type_from_gl_func(func: str) -> str:
    """
    Helper function to traverse the dict of JSON funcs to determine a uniform's type.
    Throws AssertionError if the uniform type is not known.
    :param func: the function to check.
    :return: the GLSL type of the uniform.
    """
    if func not in UNIFORM_TYPES.keys():
        raise AssertionError('Unknown uniform type: ' + func)
    return UNIFORM_TYPES[func]


def is_version_header(line: str) -> bool:
    """
    Helper function to see if a given string is a GLSL preprocessor version string.
    :param line: the line of code to check.
    :return: True if the string is a GLSL preprocessor version string, False otherwise.
    """
    return SHADER_VERSION_FLAG in line


def get_json_properties(shader_job: str) -> dict:
    """
    Helper function to parse a shader job JSON file into a dict of properties.
    Throws IOError if the file can't be parsed.
    :param shader_job: the path to the shader job file.
    :return: a dict of JSON properties.
    """
    with gfuzz_common.open_helper(shader_job, 'r') as job:
        json_parsed = json.load(job)
    return json_parsed


def get_shader_from_job(shader_job: str) -> str:
    """
    Helper function to get the filename of a shader file from its corresponding shader job.
    :param shader_job: the path of the shader job file.
    :return: the path of the fragment shader file.
    """
    return gfuzz_common.remove_end(shader_job, '.json') + '.frag'


def get_shader_test_from_job(shader_job: str) -> str:
    """
    Helper function to get the filename of the new shader test from the given shader job.
    :param shader_job: the path of the shader job file.
    :return: the path of the shader_test file.
    """
    return gfuzz_common.remove_end(shader_job, '.json') + '.shader_test'


def main_helper(args: List[str]) -> None:
    """
    Main function. Parses arguments, delegates to other functions to write the shader_test string,
    and writes the string to file.
    :param args: the command line arguments.
    """
    description = (
        'Given a GraphicsFuzz shader job JSON file, produce a Mesa piglit shader_test file. '
        'The shader test will be the same name as the shader job.')

    argparser = argparse.ArgumentParser(description=description)

    argparser.add_argument(
        'shader_job',
        help='Path to the GraphicsFuzz shader job JSON file.')

    argparser.add_argument(
        '--nodraw',
        action='store_true',
        help='Do not draw the shader output when running the test. Useful for crash testing.'
    )

    args = argparser.parse_args(args)

    test_string = make_shader_test_string(args.shader_job, args.nodraw)

    with gfuzz_common.open_helper(get_shader_test_from_job(args.shader_job), 'w') as shader_test:
        shader_test.write(test_string)
