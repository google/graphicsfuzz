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

java_tool_path = os.sep.join(
    [os.path.dirname(os.path.abspath(__file__)), "..", "..", "jar", "tool-1.0.jar"])

# Run the reduction

cmd = ["java", "-ea", "-cp", java_tool_path, "com.graphicsfuzz.reducer.tool.GlslReduce"] \
      + sys.argv[1:]

print("Reduction command: %s" % (" ".join(cmd)))
reduce_proc = subprocess.Popen(cmd)
reduce_proc.communicate()
sys.exit(reduce_proc.returncode)
