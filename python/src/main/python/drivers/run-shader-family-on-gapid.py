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

# Types:

RunInfo = typing.NamedTuple(
    'RunInfo', [
        ('gapis_port', str),
        ('shader_source_file', str),
        ('shader_handle', str),
        ('orig_capture_id', str),
        ('output_png', str),
        ('out_dir', str),
        ('orig_capture_file', str),
        ('gapis_log', str),
        ('frag_files_dir', str)
    ])


# Regex:

replaced_capture_id_regex = re.compile('New capture id: ([a-z0-9]*)\n')

# When using gapit screenshot filename, the capture ID is output which can be a nice way to
# load a capture into gapis and get its ID.
screenshot_capture_id_regex = re.compile('Getting screenshot from capture id: ([a-z0-9])*\n')

# Commands:

gapit = ['gapit']
gapis = ['gapis']


# Functions:


def call(args: List[str]):
    print(" ".join(args))
    res = subprocess.run(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        universal_newlines=True)
    print("\nstdout:\n")
    print(res.stdout)
    print("\nstderr:\n")
    print(res.stderr)
    print("\nend.\n")
    return res


def run_gapit_screenshot_file(info: RunInfo) -> str:
    """
    Not used when executed. Could be used if imported into Python shell.
    :param info:
    :returns capture ID
    """
    res = call(gapit + [
        'screenshot',
        '-gapis-port='+info.gapis_port,
        '-out='+info.output_png,
        info.orig_capture_file
    ])

    stdout = res.stdout  # type: str
    new_capture_id = screenshot_capture_id_regex.search(stdout).group(1)

    return new_capture_id


def run_gapis(port: str):
    """
    Not used when executed. Could be used if imported into Python shell.

    :param port:
    """
    call(gapis + [
        '-persist',
        '-rpc', 'localhost:'+port,
    ])


def run_shader(info: RunInfo):
    # Call gapit replace_resource to create a new capture in gapis with the replaced shader source.
    # The new capture id is output to stdout.
    res = call(gapit + [
        'replace_resource',
        '-skipoutput=true',
        '-resourcepath='+info.shader_source_file,
        '-handle='+info.shader_handle,
        '-gapis-port='+info.gapis_port,
        '-captureid',  # This is a bool flag, not an arg that takes a string.
        info.orig_capture_id])

    stdout = res.stdout  # type: str
    new_capture_id = replaced_capture_id_regex.search(stdout).group(1)

    info = info._replace(
        output_png=str(Path(info.out_dir) / (Path(info.shader_source_file).stem + ".png"))
    )

    # Now call gapit screenshot to capture and write out the screenshot.
    res = call(gapit + [
        'screenshot',
        '-gapis-port='+info.gapis_port,
        '-out='+info.output_png,
        '-captureid',  # This is a bool flag, not an arg that takes a string.
        new_capture_id
    ])


def run_shader_family(info: RunInfo):
    frag_dir = Path(info.frag_files_dir)
    for f in frag_dir.iterdir():
        if f.is_file() and f.name.endswith('.frag'):
            info = info._replace(
                shader_source_file=str(frag_dir / f)
            )
            run_shader(info)


def go(argv):

    parser = argparse.ArgumentParser(
        formatter_class=argparse.RawDescriptionHelpFormatter,
        description="Run shaders via gapid. First:\n"
                    "$ gapis -enable-local-files -persist -rpc 'localhost:40000'\n"
                    "$ gapit screenshot -gapis-port 40000 capture.linear.gfxtrace (and make a note "
                    "of the capture id)")

    parser.add_argument("frag_files_dir", type=str, action="store",
                        help="directory containing .frag files")
    parser.add_argument("gapis_port", type=str, action="store",
                        help="port on which gapis is listening")
    parser.add_argument("shader_handle", type=str, action="store",
                        help="the shader handle within the capture in gapis that should be "
                             "replaced")
    parser.add_argument("orig_capture_id", type=str, action="store",
                        help="the capture id that is already loaded in gapis "
                             "(e.g. via gapit screenshot)")
    parser.add_argument("out_dir", type=str, action="store",
                        help="the output directory that will receive .png files")

    args = parser.parse_args(argv)

    info = RunInfo(
        args.gapis_port,
        '',
        args.shader_handle,
        args.orig_capture_id,
        '',
        args.out_dir,
        '',
        '',
        args.frag_files_dir
    )

    run_shader_family(info)


if __name__ == "__main__":
    go(sys.argv[1:])

