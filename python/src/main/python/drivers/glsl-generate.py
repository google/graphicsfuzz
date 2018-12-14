#!/usr/bin/env python

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

java_tool_path = os.sep.join(
    [os.path.dirname(os.path.abspath(__file__)), "..", "..", "jar", "tool-1.0.jar"])

# Run the generator

cmd = ["java", "-ea", "-cp", java_tool_path, "com.graphicsfuzz.generator.tool.GlslGenerate" ] + sys.argv[1:]

generate_proc = subprocess.Popen(cmd)
generate_proc.communicate()
sys.exit(generate_proc.returncode)
