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
import subprocess
import sys
from typing import List, Tuple

HERE = os.path.abspath(__file__)

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(HERE))) + os.sep + "drivers")

import runspv


#########################################
# General helper functions

def get_jar_dir():
    # graphics-fuzz/python/drivers/
    jar_dir = os.path.dirname(os.path.abspath(__file__))
    for i in range(0, 6):
        jar_dir = os.path.dirname(jar_dir)
    return os.path.join(jar_dir, 'graphicsfuzz', 'target', 'graphicsfuzz', 'jar')


def make_empty_json(path: pathlib2.Path, prefix: str = 'shader') -> pathlib2.Path:
    json = path / (prefix + '.json')
    json.write_text('{}')
    return json


def make_empty_file(path: pathlib2.Path, filename: str) -> pathlib2.Path:
    result = path / filename
    result.touch(exist_ok=False)
    return result


def is_success(output_dir: pathlib2.Path) -> bool:
    status_file = output_dir / 'STATUS'
    if not status_file.exists():
        return False
    return open(str(status_file), 'r').read().startswith('SUCCESS')


def exact_image_match(image_file_1: pathlib2.Path, image_file_2: pathlib2.Path) -> bool:
    image_1 = PIL.Image.open(str(image_file_1)).convert('RGB')
    image_2 = PIL.Image.open(str(image_file_2)).convert('RGB')
    # The dimensions should match
    if image_1.width != image_2.width:
        return False
    if image_1.height != image_2.height:
        return False
    # Every pixel should match
    for x in range(0, image_1.width):
        for y in range(0, image_1.height):
            if image_1.getpixel((x, y)) != image_2.getpixel((x, y)):
                return False
    return True


def fuzzy_image_match(image_file_1: PIL.Image, image_file_2: PIL.Image) -> bool:
    tolerance_parameters = '25 4 300 200 60 4 130 80'
    cmd = 'java -ea -cp ' + get_jar_dir() + os.sep + 'tool-1.0.jar com.graphicsfuzz.tool.FuzzyImageComparisonTool '\
          + str(image_file_1) + ' ' + str(image_file_2) + ' ' + tolerance_parameters
    return subprocess.run(cmd, shell=True).returncode == 0


def images_match(out_dir_1: pathlib2.Path, out_dir_2: pathlib2.Path,
                 fuzzy_image_comparison: bool) -> bool:
    image_file_1 = out_dir_1 / 'image_0.png'
    image_file_2 = out_dir_2 / 'image_0.png'
    assert image_file_1.exists()
    assert image_file_2.exists()
    if fuzzy_image_comparison:
        return fuzzy_image_match(image_file_1, image_file_2)
    return exact_image_match(image_file_1, image_file_2)


def get_ssbo_json(output_dir: pathlib2.Path) -> {}:
    ssbo_json = output_dir / 'ssbo.json'
    assert ssbo_json.exists()
    return json.load(open(str(ssbo_json), 'r'))


def check_images_match(tmp_path: pathlib2.Path, json: str, is_android_1: bool, is_android_2: bool,
                       is_amber_1: bool, is_amber_2: bool, fuzzy_image_comparison: bool):
    out_dir_1 = tmp_path / 'out_1'
    out_dir_2 = tmp_path / 'out_2'

    args_1 = ['android' if is_android_1 else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
                   + json, str(out_dir_1)]
    if not is_amber_1:
        args_1.append('--legacy-worker')

    args_2 = ['android' if is_android_2 else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
                   + json, str(out_dir_2)]
    if not is_amber_2:
        args_2.append('--legacy-worker')

    runspv.main_helper(args_1)
    runspv.main_helper(args_2)
    assert is_success(out_dir_1)
    assert is_success(out_dir_2)
    assert images_match(out_dir_1, out_dir_2, fuzzy_image_comparison)


def check_images_match_android_amber_vs_legacy(tmp_path: pathlib2.Path, json: str):
    check_images_match(tmp_path=tmp_path,
                       json=json,
                       is_android_1=True,
                       is_android_2=True,
                       is_amber_1=True,
                       is_amber_2=False,
                       fuzzy_image_comparison=False)


def check_images_match_host_vs_android_amber(tmp_path: pathlib2.Path, json: str):
    check_images_match(tmp_path=tmp_path,
                       json=json,
                       is_android_1=False,
                       is_android_2=True,
                       is_amber_1=True,
                       is_amber_2=True,
                       fuzzy_image_comparison=True)


def check_host_and_android_match_compute(tmp_path: pathlib2.Path, json: str):
    out_dir_host = tmp_path / 'out_legacy'
    out_dir_android = tmp_path / 'out_amber'

    args_host = ['host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep + json, str(out_dir_host)]
    args_android = ['android', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep + json, str(out_dir_android)]
    runspv.main_helper(args_host)
    runspv.main_helper(args_android)
    assert is_success(out_dir_host)
    assert is_success(out_dir_android)
    ssbo_host = get_ssbo_json(out_dir_host)
    ssbo_android = get_ssbo_json(out_dir_android)
    # Check that the SSBO dictionaries are deep-equal
    assert ssbo_host == ssbo_android


#########################################
# Helper functions for specific shaders

def simple_compute(tmp_path: pathlib2.Path, is_android: bool):
    out_dir = tmp_path / 'out'
    runspv.main_helper(['android' if is_android else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
                        + 'simple.json',
                        str(out_dir)])
    assert is_success(out_dir)
    ssbo_json = get_ssbo_json(out_dir)
    assert ssbo_json['ssbo'][0][0] == 42


def red_image(tmp_path: pathlib2.Path, is_android: bool, is_legacy_worker: bool):
    out_dir = tmp_path / 'out'
    args = ['android' if is_android else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
            + 'image_test_0007.json',
            str(out_dir)]
    if is_legacy_worker:
        args.append('--legacy-worker')
    runspv.main_helper(args)
    assert is_success(out_dir)
    image_file = out_dir / 'image_0.png'
    assert image_file.exists()
    image = PIL.Image.open(str(image_file)).convert('RGB')
    r, g, b = image.getpixel((0, 0))
    assert r == 255
    assert g == 0
    assert b == 0


def bubblesort_flag(tmp_path: pathlib2.Path, is_android: bool, is_legacy_worker: bool):
    out_dir = tmp_path / 'out'
    args = ['android' if is_android else 'host', os.path.dirname(HERE) + os.sep + 'shaders' + os.sep
            + 'image_test_0002.json',
            str(out_dir)]
    if is_legacy_worker:
        args.append('--legacy-worker')
    runspv.main_helper(args)
    assert is_success(out_dir)
    image_file = out_dir / 'image_0.png'
    assert image_file.exists()
    image = PIL.Image.open(str(image_file)).convert('RGB')
    r, g, b = image.getpixel((0, 0))
    assert r in [24, 25, 26]
    assert g in [152, 153, 154]
    assert b in [254, 255]


#########################################
# The tests

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


def test_skip_render_image_amber_host(tmp_path: pathlib2.Path):
    assert False


def test_skip_render_image_amber_android(tmp_path: pathlib2.Path):
    assert False


def test_skip_render_compute_amber_host(tmp_path: pathlib2.Path):
    assert False


def test_skip_render_compute_amber_android(tmp_path: pathlib2.Path):
    assert False


def test_simple_compute_host(tmp_path: pathlib2.Path):
    simple_compute(tmp_path, False)


def test_simple_compute_android(tmp_path: pathlib2.Path):
    simple_compute(tmp_path, True)


def test_sklansky_compute_host(tmp_path: pathlib2.Path):
    pass


def test_sklansky_compute_android(tmp_path: pathlib2.Path):
    pass


def test_kogge_stone_compute_host(tmp_path: pathlib2.Path):
    pass


def test_kogge_stone_compute_android(tmp_path: pathlib2.Path):
    pass


def test_red_image_amber_host(tmp_path: pathlib2.Path):
    red_image(tmp_path, False, False)


def test_red_image_amber_android(tmp_path: pathlib2.Path):
    red_image(tmp_path, True, False)


def test_red_image_legacy_android(tmp_path: pathlib2.Path):
    red_image(tmp_path, True, True)


def test_bubblesort_flag_amber_host(tmp_path: pathlib2.Path):
    bubblesort_flag(tmp_path, False, False)


def test_bubblesort_flag_amber_android(tmp_path: pathlib2.Path):
    bubblesort_flag(tmp_path, True, False)


def test_bubblesort_flag_legacy_android(tmp_path: pathlib2.Path):
    bubblesort_flag(tmp_path, True, True)


#################################
# Android, amber vs. legacy

def test_image_0000_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0000.json')


def test_image_0001_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0001.json')


def test_image_0002_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0002.json')


def test_image_0003_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0003.json')


def test_image_0004_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0004.json')


def test_image_0005_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0005.json')


def test_image_0006_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0006.json')


def test_image_0007_android_amber_vs_legacy(tmp_path: pathlib2.Path):
    check_images_match_android_amber_vs_legacy(tmp_path, 'image_test_0007.json')


#################################
# Android vs. host, amber

def test_image_0000_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0000.json')


def test_image_0001_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0001.json')


def test_image_0002_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0002.json')


@pytest.mark.skip(reason="Need to investigate this shader: the images produced are completely different.")
def test_image_0003_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0003.json')


def test_image_0004_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0004.json')


def test_image_0005_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0005.json')


def test_image_0006_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0006.json')


def test_image_0007_host_vs_android_amber(tmp_path: pathlib2.Path):
    check_images_match_host_vs_android_amber(tmp_path, 'image_test_0007.json')
