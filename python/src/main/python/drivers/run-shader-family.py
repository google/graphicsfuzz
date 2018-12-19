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

import argparse
import glob
import os
import shutil
import subprocess
import sys
import threading
import platform
from os import path

sys.argv.pop(0)
file_dir = os.path.dirname(os.path.abspath(__file__))
install_dir = path.join(file_dir, os.pardir, os.pardir)
cmd = ["java", "-cp", path.join(install_dir, "jar", "tool-1.0.jar"),
       "com.graphicsfuzz.shadersets.RunShaderFamily"] + sys.argv
print(" ".join(cmd))
ret_code = subprocess.call(cmd)
sys.exit(ret_code)
