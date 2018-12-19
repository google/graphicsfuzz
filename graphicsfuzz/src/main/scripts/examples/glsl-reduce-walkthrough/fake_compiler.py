#!/usr/bin/python3

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
import sys

if len(sys.argv) != 2:
    sys.stderr.write("Usage: " + sys.argv[0] + " <shader>\n")
    exit(1)

filename = sys.argv[1]

if not os.path.isfile(filename):
    sys.stderr.write("Input file " + filename + " does not exist.\n")
    exit(1)

with open(filename, "r") as f:
    text = f.read()

# Pretend that an internal error occurred if the file doesn't contain 'floor'.
if "floor" not in text:
    sys.stderr.write("Internal error: something went wrong inlining 'floor'.\n")
    exit(2)

# Pretend that a fatal error occurred if the file contains '[i]' at least twice.
if text.count("[i]") > 1:
    sys.stderr.write("Fatal error: too much indexing.\n")
    exit(2)

# Pretend that compilation succeeded.
sys.stdout.write("Compilation succeeded [not really!]\n")
exit(0)
