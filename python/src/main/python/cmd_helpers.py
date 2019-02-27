
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

import subprocess
import os

HERE = os.path.abspath(__file__)

path = os.path.join


def get_tool_path():
    return path(get_bin_jar_dirs()[1], "tool-1.0.jar")


def get_bin_jar_dirs():
    def try_get_jar_bin_dirs(install_root):
        bin_dir = path(install_root, "bin")
        jar_dir = path(install_root, "jar")
        if os.path.isdir(bin_dir) and os.path.isdir(jar_dir):
            return os.path.abspath(bin_dir), os.path.abspath(jar_dir)
        return None

    # Perhaps we are running from the IDE.  Check this first, since the deployed files are likely also present if
    # running from the IDE.
    res = try_get_jar_bin_dirs(path(os.path.dirname(HERE), os.pardir, os.pardir, os.pardir, os.pardir, "graphicsfuzz",
                                    "target", "graphicsfuzz"))
    if res is not None:
        return res

    # Perhaps we are running from the zip.
    res = try_get_jar_bin_dirs(path(os.path.dirname(HERE), os.pardir))
    if res is not None:
        return res

    raise Exception("Could not find bin and jar directories")


def get_shaders_dir():

    # Perhaps we are running from the IDE.  Check this first, since the deployed files are likely also present if
    # running from the IDE.
    res = path(os.path.dirname(HERE), os.pardir, os.pardir, os.pardir, os.pardir, "shaders", "src", "main", "glsl")
    if os.path.isdir(res):
        return os.path.abspath(res)

    # Perhaps we are running from the zip.
    res = path(os.path.dirname(HERE), os.pardir, "shaders")
    if os.path.isdir(res):
        return os.path.abspath(res)

    raise Exception("Could not find shaders directory")


def execute(cmd, verbose):
    if verbose:
        print("Validator command: " + " ".join(cmd))
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    validator_stdout, validator_stderr = proc.communicate()
    assert (proc.returncode is not None)
    return {"returncode": proc.returncode,
            "stdout": validator_stdout,
            "stderr": validator_stderr}


def validate_frag(frag_file, validator, verbose):
    cmd = [validator, frag_file]
    return execute(cmd, verbose)
