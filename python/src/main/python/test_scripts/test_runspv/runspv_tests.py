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


import json
import os
import pathlib2
import PIL.Image
import pytest
import sys

HERE = os.path.abspath(__file__)

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(HERE))) + os.sep + "drivers")

import runspv


# Helpers

def make_empty_json(tmp_path: pathlib2.Path, prefix: str = 'shader') -> pathlib2.Path:
    json = tmp_path / (prefix + '.json')
    json.write_text('{}')
    return json


def make_empty_file(tmp_path: pathlib2.Path, filename: str) -> pathlib2.Path:
    result = tmp_path / filename
    result.touch(exist_ok=False)
    return result


# Tests

def test_error_bad_target():
    # Check that an appropriate error is raised if a bad target (atari) is provided.
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['atari', 'someshader.json', 'out'])
    assert 'ValueError: Target must be' in str(value_error)


def test_no_force_with_host():
    # Check that --force option cannot be passed with host target.
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', 'someshader.json', '--force', 'out'])
    assert 'ValueError: "force" option not compatible with "host" target' in str(value_error)


def test_no_serial_with_host():
    # Check that --serial option cannot be passed with host target.
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', 'someshader.json', '--serial', 'ABCDEFG', 'out'])
    assert 'ValueError: "serial" option not compatible with "host" target' in str(value_error)


def test_error_no_json(tmp_path: pathlib2.Path):
    # Check that an appropriate error is raised if the given JSON filename does not exist.
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(tmp_path / 'someshader.json'), 'out'])
    assert 'ValueError: The given JSON file does not exist' in str(value_error)


def test_error_no_shaders(tmp_path: pathlib2.Path):
    # Check for appropriate error when no .asm or .spv files are passed
    json = make_empty_json(tmp_path)
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: No compute nor vertex shader files found' in str(value_error)


def test_error_compute_asm_and_spv(tmp_path: pathlib2.Path):
    # Check for appropriate error when both .asm and .spv files are provided for a compute shader.
    json = make_empty_json(tmp_path, "shader")
    make_empty_file(tmp_path, "shader.comp.asm")
    make_empty_file(tmp_path, "shader.comp.spv")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: More than one of .comp, .comp.asm and .comp.spv are present' in str(value_error)


def test_error_compute_asm_and_glsl(tmp_path: pathlib2.Path):
    # Check for appropriate error when both .asm and .spv files are provided for a compute shader.
    json = make_empty_json(tmp_path, "shader")
    make_empty_file(tmp_path, "shader.comp.asm")
    make_empty_file(tmp_path, "shader.comp")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: More than one of .comp, .comp.asm and .comp.spv are present' in str(value_error)


def test_error_vert_asm_and_spv(tmp_path: pathlib2.Path):
    # Check for appropriate error when both .asm and .spv files are provided for a vertex shader.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.vert.asm")
    make_empty_file(tmp_path, "shader.vert.spv")
    make_empty_file(tmp_path, "shader.frag.asm")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: More than one of .vert, .vert.asm and .vert.spv are present' in str(value_error)


def test_error_vert_asm_and_glsl(tmp_path: pathlib2.Path):
    # Check for appropriate error when both .asm and .spv files are provided for a vertex shader.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.vert.asm")
    make_empty_file(tmp_path, "shader.vert")
    make_empty_file(tmp_path, "shader.frag")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: More than one of .vert, .vert.asm and .vert.spv are present' in str(value_error)


def test_error_frag_asm_and_spv(tmp_path: pathlib2.Path):
    # Check for appropriate error when both .asm and .spv files are provided for a fragment shader.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.vert.spv")
    make_empty_file(tmp_path, "shader.frag.asm")
    make_empty_file(tmp_path, "shader.frag.spv")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: More than one of .frag, .frag.asm and .frag.spv are present' in str(value_error)


def test_error_compute_and_frag(tmp_path: pathlib2.Path):
    # Check for appropriate error when both compute and fragment shaders are provided.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.comp.spv")
    make_empty_file(tmp_path, "shader.frag.asm")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: Compute shader cannot coexist with vertex/fragment shaders' in str(value_error)


def test_error_compute_and_vert(tmp_path: pathlib2.Path):
    # Check for appropriate error when both compute and vertex shaders are provided.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.comp.asm")
    make_empty_file(tmp_path, "shader.frag.spv")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: Compute shader cannot coexist with vertex/fragment shaders' in str(value_error)


def test_error_frag_no_vert(tmp_path: pathlib2.Path):
    # Check for appropriate error when a fragment shader but no vertex shader is provided.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.frag.asm")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: No compute nor vertex shader files found' in str(value_error)


def test_error_vert_no_frag(tmp_path: pathlib2.Path):
    # Check for appropriate error when a vertex shader but no fragment shader is provided.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.vert.spv")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out')])
    assert 'ValueError: Vertex shader but no fragment shader found' in str(value_error)


def test_no_compute_in_legacy(tmp_path: pathlib2.Path):
    # Check for appropriate error when legacy worker is attempted to be used with compute shader.
    json = make_empty_json(tmp_path)
    make_empty_file(tmp_path, "shader.comp")
    with pytest.raises(ValueError) as value_error:
        runspv.main_helper(['host', str(json), str(tmp_path / 'out'), '--legacy-worker'])
    assert 'ValueError: Compute shaders are not supported with the legacy worker' in str(value_error)


def simple_compute(tmp_path: pathlib2.Path, is_android: bool):
    out_dir = tmp_path / 'out'
    runspv.main_helper(['android' if is_android else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
                        + 'simple.json',
                        str(out_dir)])
    status_file = out_dir / 'STATUS'
    assert status_file.exists()
    assert open(str(status_file), 'r').read().startswith('SUCCESS')
    ssbo_json = out_dir / 'ssbo.json'
    assert ssbo_json.exists()
    with open(str(ssbo_json), 'r') as f:
        j = json.load(f)
    assert j['ssbo'][0][0] == 42


def test_simple_compute_host(tmp_path: pathlib2.Path):
    simple_compute(tmp_path, False)


def test_simple_compute_android(tmp_path: pathlib2.Path):
    simple_compute(tmp_path, True)


def red_image(tmp_path: pathlib2.Path, is_android: bool, is_legacy_worker: bool):
    out_dir = tmp_path / 'out'
    args = ['android' if is_android else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
                        + 'red.json',
                        str(out_dir)]
    if is_legacy_worker:
        args.append('--legacy-worker')
    runspv.main_helper(args)
    status_file = out_dir / 'STATUS'
    assert status_file.exists()
    assert open(str(status_file), 'r').read().startswith('SUCCESS')
    image_file = out_dir / 'image_0.png'
    assert image_file.exists()
    image = PIL.Image.open(str(image_file)).convert('RGB')
    r, g, b = image.getpixel((0, 0))
    assert r == 255
    assert g == 0
    assert b == 0


def test_red_image_amber_host(tmp_path: pathlib2.Path):
    red_image(tmp_path, False, False)


def test_red_image_amber_android(tmp_path: pathlib2.Path):
    red_image(tmp_path, True, False)


def test_red_image_legacy_host(tmp_path: pathlib2.Path):
    red_image(tmp_path, False, True)


def test_red_image_legacy_android(tmp_path: pathlib2.Path):
    red_image(tmp_path, True, True)
