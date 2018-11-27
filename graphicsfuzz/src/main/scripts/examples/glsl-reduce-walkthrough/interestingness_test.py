#!/usr/bin/python

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

frag = os.path.splitext(sys.argv[1])[0] + ".frag"
print(frag)

cmd = [ "python", os.path.dirname(os.path.realpath(__file__)) + os.sep + "fake_compiler.py", frag ]

proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
proc_stdout, proc_stderr = proc.communicate()

if proc.returncode != 0:
  if "too much indexing" in proc_stderr:
    # Interesting: the compiler failed with a relevant message.
    exit(0)

# Boring: the compiler either succeeded, or failed with an irrelevant message.
exit(1)
