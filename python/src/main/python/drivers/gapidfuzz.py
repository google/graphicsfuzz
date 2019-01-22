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
import argparse
from pathlib import Path
from typing import List
import subprocess
import re
import typing
import io
glsl_generate = __import__("glsl-generate")

# Types:


class RunInfo(object):
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


ri = RunInfo()

# Regex:

replaced_capture_id_regex = re.compile('New capture id: ([a-z0-9]*)\n')

# When using gapit screenshot filename, the capture ID is output which can be a nice way to
# load a capture into gapis and get its ID.
screenshot_capture_id_regex = re.compile('Getting screenshot from capture id: ([a-z0-9]*)\n')


# Functions:


def nz(s):
    assert len(s) > 0
    return s


def remove_end(s: str, end: str):
    assert s.endswith(end)
    return s[:-len(end)]


def remove_start(s: str, start: str):
    assert s.startswith(start)
    return s[len(start):]


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


def run_gapit_screenshot_file(info: RunInfo):
    res = call([
        nz(info.gapit),
        'screenshot',
        '-gapis-port='+nz(info.gapis_port),
        '-out='+nz(info.output_png),
        nz(info.orig_capture_file)
    ])

    stdout = res.stdout  # type: str
    info.orig_capture_id = screenshot_capture_id_regex.search(stdout).group(1)


def run_gapit_screenshot_capture_id(info: RunInfo, capture_id: str, out: str):
    res = call([
        nz(info.gapit),
        'screenshot',
        '-gapis-port='+nz(info.gapis_port),
        '-out='+nz(out),
        '-captureid',
        nz(capture_id)
    ])


def dump_shaders_trace_id(info: RunInfo):

    Path(info.shaders_dir).mkdir(parents=True, exist_ok=True)

    call([
        nz(info.gapit),
        'dump_resources',
        '-captureid',
        '-gapis-port='+nz(info.gapis_port),
        nz(info.orig_capture_id)
    ], cwd=nz(info.shaders_dir))


def run_gapis_async(info: RunInfo):
    info.gapis_process = call_async([
        nz(info.gapis),
        '-enable-local-files',
        '-persist',
        '-rpc', 'localhost:'+nz(info.gapis_port),
    ])


def run_gapis(info: RunInfo):
    run_gapis_async(info)
    info.gapis_process.communicate()


def process_shaders(info: RunInfo):
    for f in Path(info.shaders_dir).iterdir():  # type: Path
        # TODO: change this once gapit outputs shaders with .frag extension.
        if f.name.endswith(".Fragment"):
            frag_file = f.with_name(remove_end(f.name, ".Fragment") + ".frag")
            json_file = f.with_name(remove_end(f.name, ".Fragment") + ".json")
            print(frag_file)
            f.rename(frag_file)
            with io.open(str(json_file), "w", encoding='utf-8') as json:
                json.write("{}\n")
            print(json_file)


def fuzz_shaders(info: RunInfo):

    # glsl-generate --seed 0 references donors 10 100 test out --no-injection-switch
    res = glsl_generate.go([
        "--seed", nz(str(info.seed)),
        nz(info.shaders_dir),
        nz(info.donors),
        nz(str(info.num_variants)),
        "100",
        "family",
        nz(info.families_dir),
        "--no-injection-switch",
    ])

    assert res == 0, "glsl-generate failed"


def create_traces(info: RunInfo):
    handle_to_variant_list = {}  # type: typing.Dict[str, typing.List[str]]
    for family in Path(info.families_dir).iterdir():  # type: Path
        if family.name.startswith("family_"):
            shader_handle = remove_start(family.name, "family_")
            if info.specific_handle is not None and info.specific_handle != shader_handle:
                continue
            variant_shaders = list(family.glob("variant_*.frag"))
            # Prepend directory:
            variant_shaders = [str(v) for v in variant_shaders]
            handle_to_variant_list[shader_handle] = variant_shaders

    for i in range(0, info.num_variants):
        if info.specific_variant_index is not None and info.specific_variant_index != i:
            continue
        output_prefix = info.output_capture_prefix + '{:03d}'.format(i)
        output_file =  output_prefix + ".gfxtrace"
        output_screenshot =  output_prefix + ".png"
        # For each shader handle, replace the shader with variant i.
        # After each replacement, we get a new trace, so we overwrite the capture id.
        capture_id = nz(info.orig_capture_id)
        for handle, variants in handle_to_variant_list.items():
            # TODO: could optimize by only outputting file on final replacement.
            capture_id = run_replace_shader(info, capture_id, handle, variants[i], output_file)

        # While we have the capture id, get a screenshot;
        # it might be difficult otherwise due to file caching.
        if info.get_screenshots:
            run_gapit_screenshot_capture_id(info, capture_id, output_screenshot)

    print("Warning: gapis caches the contents of files, and the new trace files are usually "
          "rewritten several times, so gapis may not be able to read the latest contents. "
          "You may need to restart gapis.")


def run_replace_shader(info: RunInfo, capture_id: str, shader_handle: str, shader_source_file: str,
                       output_file: str):
    # Call gapit replace_resource to create a new capture in gapis with the replaced shader source.
    # The new capture id is output to stdout.
    res = call([
        nz(info.gapit),
        'replace_resource',
        '-skipoutput=true' if output_file is None else '-outputtracefile='+output_file,
        '-resourcepath='+nz(shader_source_file),
        '-handle='+nz(shader_handle),
        '-gapis-port='+nz(info.gapis_port),
        '-captureid',  # This is a bool flag, not an arg that takes a string.
        nz(capture_id)])

    stdout = res.stdout  # type: str
    new_capture_id = replaced_capture_id_regex.search(stdout).group(1)

    return new_capture_id


def fuzz_trace(info: RunInfo):

    run_gapis_async(info)

    # This is one way of loading the capture into gapis and getting its id.
    # TODO: this requires the device to be present, so use a different approach to load capture.
    run_gapit_screenshot_file(info)

    dump_shaders_trace_id(info)
    process_shaders(info)
    fuzz_shaders(info)
    create_traces(info)


def go(argv):

    # Combine formatters using multiple inheritance.
    class ArgParseFormatter(argparse.RawDescriptionHelpFormatter,
                            argparse.ArgumentDefaultsHelpFormatter):
        pass

    parser = argparse.ArgumentParser(
        formatter_class=ArgParseFormatter,
        description="Fuzz traces. Ensure there exists:\n\n"
                    " - capture.gfxtrace\n"
                    " - donors/\n\n"
                    "Alternatively, do:\n\n"
                    "ipython3\n"
                    "from gapidfuzz import *\n"
                    "# optional: modify parameters\n"
                    "ri.get_screenshots = True\n"
                    "fuzz_shaders(ri)\n")

    parser.add_argument("-capture_file",
                        type=str,
                        action="store",
                        default="capture.gfxtrace",
                        help="trace file to fuzz")

    parser.add_argument("-donors",
                        type=str,
                        action="store",
                        default="donors",
                        help="directory of donor shader jobs")

    parser.add_argument("-output_capture_prefix",
                        type=str,
                        action="store",
                        default="capture_",
                        help="prefix path and filename for generated trace files")

    args = parser.parse_args(argv)

    ri.orig_capture_file = args.capture_file
    ri.donors = args.donors
    ri.output_capture_prefix = args.output_capture_prefix

    fuzz_trace(ri)


if __name__ == "__main__":
    go(sys.argv[1:])

