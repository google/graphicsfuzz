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
import os
from typing import List

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

# Strings used to specify the GL version to use in a piglit test's
# [require] header.
GLSL_VERSION_STRING = 'GLSL >= '
GLSLES_VERSION_STRING = 'GLSL ES >= '

# GLSL preprocessor version flag.
SHADER_VERSION_FLAG = '#version'
# GLES specifier in version string.
ES_SPECIFIER = ' es'
# GLSL version headers specify a version without a decimal point, when piglit takes a version
# string with a decimal point. The easiest way of getting this is by dividing by 100.
SHADER_VERSION_FACTOR = 100


def make_shader_test_string(shader_job: str) -> str:
    """
    Makes a piglit shader_test from a shader job and shader.
    :param shader_job: The path to the shader job file
    :return: the shader_test
    """
    shader_job_json_parsed = get_json_properties(shader_job)

    with open(get_shader_from_job(shader_job), 'r',
              encoding='utf-8', errors='ignore') as shader:
        shader_file_string = shader.read()

    shader_lines = shader_file_string.split('\n')
    shader_test_string = str('')
    # The version header always has to be on the first line of the shader.
    shader_version_header = shader_lines[0]

    shader_test_string += make_require_header(shader_version_header) + '\n'
    shader_test_string += make_vertex_shader_header() + '\n'
    shader_test_string += make_fragment_shader_header(shader_file_string) + '\n'
    shader_test_string += make_test_header(shader_job_json_parsed, shader_lines)

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
    require_header += GLSLES_VERSION_STRING if ES_SPECIFIER in shader_version_header \
        else GLSL_VERSION_STRING
    try:
        shader_version = shader_version_header.split(' ')[1]
    except IndexError:
        raise IOError('Malformed shader - invalid GLSL version string.')
    if not shader_version.isdigit():
        raise IOError('Malformed shader - invalid GLSL version string.')
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


def make_test_header(shader_job_json_parsed: List, shader_lines: List) -> str:
    """
    Creates the [test] header. Loads uniforms based on the uniforms found in the shader.
    Note that piglit really doesn't like it if you try to load a uniform that isn't actually
    used in the shader - because of this, we actually have to parse the shader itself and
    find out which uniforms are being used instead of blindly pasting from the shader job JSON file.
    :param shader_job_json_parsed: the parsed JSON properties.
    :param shader_lines: the shader code parsed into a list.
    :return: the shader_test test header string.
    """
    test_header = TEST_HEADER + '\n'
    uniforms_in_shader = get_uniforms_from_shader(shader_lines)
    for dec in uniforms_in_shader:
        dec = dec.strip(';')
        test_header += dec + ' '
        variable_name = dec.split(' ')[-1]
        if variable_name not in shader_job_json_parsed:
            raise IOError('Malformed shader job - ' + dec + ' not found in job.')
        uniform_args = shader_job_json_parsed.get(variable_name).get('args')
        test_header += str(uniform_args[0:]).strip('[]').replace(',', '') + '\n'
    test_header += DRAW_COMMAND
    return test_header


def get_uniforms_from_shader(shader_lines: List) -> List:
    """
    :param shader_lines: the shader code parsed into a list
    :return: a list of all uniform declarations in the shader
    """
    return (line for line in shader_lines if is_uniform(line))


def is_uniform(line: str) -> bool:
    """
    Helper function to see if a given string is a GLSL uniform declaration.
    :param line: the line to check
    :return: True if the line is a uniform declaration, False otherwise.
    """
    return 'uniform' in line


def is_version_header(line: str) -> bool:
    """
    Helper function to see if a given string is a GLSL preprocessor version string.
    :param line: the line of code to check
    :return: True if the string is a GLSL preprocessor version string, False otherwise.
    """
    return SHADER_VERSION_FLAG in line


def get_json_properties(shader_job: str) -> List:
    """
    Helper function to parse a shader job JSON file into a list of properties.
    Throws IOError if the file can't be parsed.
    :param shader_job: the path to the shader job file.
    :return: a list of JSON properties.
    """
    with open(shader_job, 'r', encoding='utf-8', errors='ignore') as job:
        json_parsed = json.load(job)
    if not json_parsed:
        raise IOError('Malformed shader job file.')
    return json_parsed


def get_shader_from_job(shader_job: str) -> str:
    """
    Helper function to get the filename of a shader file from its corresponding shader job.
    :param shader_job: the path of the shader job file
    :return: the path of the fragment shader file.
    """
    return shader_job.split('.')[0] + '.frag'


def get_shader_test_from_job(shader_job: str) -> str:
    """
    Helper function to get the filename of the new shader test from the given shader job.
    :param shader_job: the path of the shader job file
    :return: the path of the shader_test file
    """
    return shader_job.split('.')[0] + '.shader_test'


def main_helper(args: List[str]) -> None:
    """
    Main function. Parses arguments, delegates to other functions to write the shader_test string,
    and writes the string to file.
    :param args: the command line arguments
    """
    description = (
        'Given a GraphicsFuzz shader job JSON file, produce a Mesa piglit shader_test file. '
        'The shader test will be the same name as the shader job.')

    argparser = argparse.ArgumentParser(description=description)

    argparser.add_argument(
        'shader_job', 
        help='Path to the GraphicsFuzz shader job JSON file.')
    
    args = argparser.parse_args(args)
  
    test_string = make_shader_test_string(args.shader_job)

    with open(get_shader_test_from_job(args.shader_job), 'w',
              encoding='utf-8', errors='ignore') as shader_test:
        shader_test.write(test_string)
