#!/usr/bin/env python3

# Copyright 2018 The GraphicsFuzz Project Authors
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

import sys
from pathlib import Path
from typing import List
import subprocess
import re
import typing
import io
import pprint

import gfuzz_common
glsl_generate = __import__("glsl-generate")

# Types:


class Params(object):
    def __init__(self):
        self.gapis = 'gapis'
        self.gapit = 'gapit'
        self.gapis_port = '40000'
        self.output_png = 'output.png'
        self.orig_capture_id = ''
        self.orig_capture_file = 'capture.gfxtrace'
        self.output_capture_prefix = 'capture_'
        self.shaders_dir = 'shaders'
        self.donors = 'donors'
        self.families_dir = 'families'
        self.num_variants = 10
        self.seed = 0
        # Only replace a specific shader handle in the trace.
        self.specific_handle = None  # type: str
        # Only use a specific variant index.
        self.specific_variant_index = None  # type: int
        self.gapis_process = None  # type: subprocess.Popen
        self.get_screenshots = False
        self.just_frag = False

    def __str__(self):
        return pprint.pformat(self.__dict__)

    def __repr__(self):
        return pprint.pformat(self.__dict__)


p = Params()

# Regex:

replaced_capture_id_regex = re.compile('New capture id: ([a-z0-9]*)\n')

# When using gapit screenshot filename, the capture ID is output which can be a nice way to
# load a capture into gapis and get its ID.
load_capture_id = re.compile('Loaded capture; id: ([a-z0-9]*)\n')


# Functions:


def nz(s):
    assert len(s) > 0
    return s


def call(args: List[str], cwd=None):
    print(" ".join(args))
    res = subprocess.run(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        universal_newlines=True,
        cwd=cwd)
    print("\nstdout:\n")
    print(res.stdout)
    print("\nstderr:\n")
    print(res.stderr)
    print("\nend.\n")
    return res


def call_async(args: List[str]):
    print(" ".join(args))
    return subprocess.Popen(
        args
    )


def run_gapit_screenshot_file(params: Params):
    res = call([
        nz(params.gapit),
        'screenshot',
        '-gapis-port='+nz(params.gapis_port),
        '-out='+nz(params.output_png),
        nz(params.orig_capture_file)
    ])

    stdout = res.stdout  # type: str
    params.orig_capture_id = load_capture_id.search(stdout).group(1)


def run_gapit_commands_file(params: Params):
    res = call([
        nz(params.gapit),
        'commands',
        '-gapis-port='+nz(params.gapis_port),
        nz(params.orig_capture_file)
    ])
    stdout = res.stdout  # type: str
    params.orig_capture_id = load_capture_id.search(stdout).group(1)


def run_gapit_screenshot_capture_id(params: Params, capture_id: str, out: str):
    call([
        nz(params.gapit),
        'screenshot',
        '-gapis-port='+nz(params.gapis_port),
        '-out='+nz(out),
        '-captureid',
        nz(capture_id)
    ])


def dump_shaders_trace_id(params: Params):

    # mkdir -p shaders_dir
    Path(params.shaders_dir).mkdir(parents=True, exist_ok=True)

    # gapit dump_resources
    call([
        nz(params.gapit),
        'dump_resources',
        '-captureid',
        '-gapis-port='+nz(params.gapis_port),
        nz(params.orig_capture_id)
    ], cwd=nz(params.shaders_dir))


def run_gapis_async(params: Params):
    if params.gapis is None:
        print("Skipping gapis")
        return

    params.gapis_process = call_async([
        nz(params.gapis),
        '-enable-local-files',
        '-persist',
        '-rpc', 'localhost:'+nz(params.gapis_port),
    ])


def run_gapis(params: Params):
    run_gapis_async(params)
    params.gapis_process.communicate()


def is_shader_extension(f: str):
    return f.endswith(".frag") or \
           f.endswith(".vert") or \
           f.endswith(".comp")


def process_shaders(params: Params):
    # Add .json file for every shader.
    for f in Path(params.shaders_dir).iterdir():  # type: Path
        if not is_shader_extension(f.name):
            continue
        if params.just_frag and not f.name.endswith(".frag"):
            continue
        json_file = f.with_name(f.stem + ".json")
        with io.open(str(json_file), "w", encoding='utf-8') as json:
            json.write("{}\n")
        print(json_file)


def fuzz_shaders(params: Params):
    # Generate shader families from the set of references (extracted shaders).

    assert Path(params.donors).is_dir(), "Missing donors directory: "+params.donors

    # for reference: glsl-generate --seed 0 references donors 10 prefix out --no-injection-switch
    res = glsl_generate.go([
        "--seed", nz(str(params.seed)),
        "--disable-shader-translator",
        nz(params.shaders_dir),
        nz(params.donors),
        nz(str(params.num_variants)),
        "family",
        nz(params.families_dir),
        "--no-injection-switch",
    ])

    assert res == 0, "glsl-generate failed"


def create_traces(params: Params):

    # Create map from shader handle to list of variant paths.
    # E.g. "Shader<001><002>" -> ["path_to/variant_001.frag", "path_to/variant_002.frag]
    handle_to_variant_list = {}  # type: typing.Dict[str, typing.List[str]]
    for family in Path(params.families_dir).iterdir():  # type: Path
        if family.name.startswith("family_"):
            shader_handle = gfuzz_common.remove_start(family.name, "family_")
            if params.specific_handle is not None and params.specific_handle != shader_handle:
                continue
            variant_shaders = sorted(list(family.glob("variant_*.frag")))
            if len(variant_shaders) == 0:
                variant_shaders = sorted(list(family.glob("variant_*.vert")))
            if len(variant_shaders) == 0:
                variant_shaders = sorted(list(family.glob("variant_*.comp")))
            assert(len(variant_shaders) > 0)
            # to string
            variant_shaders = [str(v) for v in variant_shaders]
            handle_to_variant_list[shader_handle] = variant_shaders

    # For each variant index i, create capture_i.gfxtrace with all shaders replaced with variant i.
    for variant_index in range(0, params.num_variants):
        if params.specific_variant_index is not None and \
                params.specific_variant_index != variant_index:
            continue
        output_prefix = params.output_capture_prefix + '{:03d}'.format(variant_index)
        output_file = output_prefix + ".gfxtrace"
        output_screenshot = output_prefix + ".png"
        # For each shader handle, replace the shader with variant i.
        # After each replacement, we get a new trace, so we overwrite the capture id.
        capture_id = nz(params.orig_capture_id)

        items = list(handle_to_variant_list.items())
        for shader_handle_index in range(len(items)):
            handle, variants = items[shader_handle_index]
            is_last_shader_handle = shader_handle_index == len(items) - 1
            capture_id = run_replace_shader(
                params,
                capture_id,
                handle,
                variants[variant_index],
                output_file if is_last_shader_handle else None)

        # While we have the capture id, get a screenshot;
        # it might be difficult otherwise due to file caching.
        if params.get_screenshots:
            run_gapit_screenshot_capture_id(params, capture_id, output_screenshot)

    print("Warning: gapis caches the contents of files, and the new trace files are usually "
          "rewritten several times, so gapis may not be able to read the latest contents. "
          "You may need to restart gapis.")


def run_replace_shader(params: Params, capture_id: str, shader_handle: str, shader_source_file: str,
                       output_file: str):
    # Call gapit replace_resource to create a new capture in gapis with the replaced shader source.
    # The new capture id is output to stdout.
    res = call([
        nz(params.gapit),
        'replace_resource',
        '-skipoutput=true' if output_file is None else '-outputtracefile='+output_file,
        '-resourcepath='+nz(shader_source_file),
        '-handle='+nz(shader_handle),
        '-gapis-port='+nz(params.gapis_port),
        '-captureid',  # This is a bool flag, not an arg that takes a string.
        nz(capture_id)])

    stdout = res.stdout  # type: str
    new_capture_id = replaced_capture_id_regex.search(stdout).group(1)

    return new_capture_id


def fuzz_trace(p: Params):
    # Shadowing p here so that you can copy and paste these into an ipython 3 shell.

    # run_gapis_async(p)

    # We should find a better way to load the capture;
    # getting a screenshot is unnecessarily heavy.
    if len(p.orig_capture_id) == 0:
        run_gapit_screenshot_file(p)

    dump_shaders_trace_id(p)
    process_shaders(p)
    fuzz_shaders(p)
    create_traces(p)


def go(argv):

    print("\n\nParams p object:\n\n" + str(p) + "\n")

    print("""
Usage: gapidfuzz [PYTHON_CODE]

E.g.
$ gapidfuzz

Or:
$ gapidfuzz ' p.just_frag=False; p.donors="shaders/corpus"; p.gapis=None; '

Ensure you have:
- a recent version of gapid master
- gapit on PATH (add gapid/bazel-bin/pkg to PATH)
- gapis running on port 40000 and with local file access enabled. You can start it with this command:
    gapis -enable-local-files -persist -rpc localhost:40000
  and kill it later with:
    killall -6 gapis
- a 'donors/' directory (in current directory)
- a 'capture.gfxtrace' OpenGL trace file (in current directory)

This script can be run from the source tree or from the release zip.

However, more fine-grained use is available via ipython3.
You must be in drivers/ or use export PYTHONPATH=/path/to/python/drivers

$ ipython3
from gapidfuzz import *
p.just_frag = False
p.orig_capture_file = "game/game.gfxtrace"
fuzz_trace(p)  # do everything
fuzz_shaders(p)  # just do the glsl-generate command (already extracted shaders)

Take a look at the Params class for the parameters that can be changed. Or just do:
$ p

""")

    # We could kill gapis automatically, but gapis may start gapir and killing a process tree
    # in a portable way is nontrivial, annoyingly.

    assert len(argv) <= 1

    if len(argv) == 1:
        print("Executing: " + argv[0])
        exec(argv[0])

    fuzz_trace(p)


if __name__ == "__main__":
    go(sys.argv[1:])
