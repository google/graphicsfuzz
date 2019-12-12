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

import os
import subprocess
import sys

HERE = os.path.abspath(__file__)

sys.path.insert(0, os.path.dirname(os.path.dirname(HERE)))

import cmd_helpers


def main_helper(argv):
    java_tool_path = cmd_helpers.get_tool_path()
    print(java_tool_path)

    # Run the tool

    cmd = ["java", "-ea", "-cp", java_tool_path] + argv
    print(cmd)

    generate_proc = subprocess.Popen(cmd)
    generate_proc.communicate()
    return generate_proc.returncode


if __name__ == "__main__":
    sys.exit(main_helper(sys.argv[1:]))
