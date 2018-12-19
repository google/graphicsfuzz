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
import copy
import subprocess
import sys

def make_spirv_opt_command(spirvopt, spv_file, opts):
  return [ spirvopt, spv_file, "-o", "out.spv" ] + [ "--" + x for x in opts ]

def run_spirv_opt(spirvopt, spv_file, opts):
  cmd = make_spirv_opt_command(spirvopt, spv_file, opts)
  print("Running command: " + " ".join(cmd))
  spirvopt_proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  return spirvopt_proc.communicate()

def interesting(spirvopt, spv_file, opts, error_string):
  stdout, stderr = run_spirv_opt(spirvopt, spv_file, opts)
  return error_string in stderr or error_string in stdout

# These are the options that spirv-opt reports using when -O is passed.
default_opts = [
    "merge-return",
    "inline-entry-points-exhaustive",
    "eliminate-dead-code-aggressive",
    "private-to-local",
    "eliminate-local-single-block",
    "eliminate-local-single-store",
    "eliminate-dead-code-aggressive",
    "scalar-replacement=100",
    "convert-local-access-chains",
    "eliminate-local-single-block",
    "eliminate-local-single-store",
    "eliminate-dead-code-aggressive",
    "eliminate-local-multi-store",
    "eliminate-dead-code-aggressive",
    "ccp",
    "eliminate-dead-code-aggressive",
    "redundancy-elimination",
    "simplify-instructions",
    "vector-dce",
    "eliminate-dead-inserts",
    "eliminate-dead-branches",
    "simplify-instructions",
    "if-conversion",
    "copy-propagate-arrays",
    "reduce-load-size",
    "eliminate-dead-code-aggressive",
    "merge-blocks",
    "redundancy-elimination",
    "eliminate-dead-branches",
    "merge-blocks",
    "simplify-instructions"
    ]

parser = argparse.ArgumentParser(description="Find minimal set of spirv-opt flags that cause a given error to be emitted.")

# Required arguments
parser.add_argument("spirv_opt_path", type=str, action="store",
                    help="Path to spirv-opt command.")
parser.add_argument("spv_file", type=str, action="store",
                    help="The SPIR-V binary file that triggers the failure.")
parser.add_argument("error_string", type=str, action="store",
                    help="String associated with the failure of interest.")

args = parser.parse_args()

if not interesting(args.spirv_opt_path, args.spv_file, default_opts, args.error_string):
  print("The failure of interest did not trigger.")
  sys.exit(1)

remaining_opts = copy.deepcopy(default_opts)

changed = True
while changed:
  changed = False
  index = 0
  while index < len(remaining_opts):
    new_opts = copy.deepcopy(remaining_opts)
    removed_opt = new_opts[index]
    del new_opts[index]
    print("Considering removing " + removed_opt)
    if interesting(args.spirv_opt_path, args.spv_file, new_opts, args.error_string):
      print("Succeeded.")
      changed = True
      remaining_opts = new_opts
    else:
      print("Failed.")
      index += 1

print("Command to trigger failure: " + " ".join(make_spirv_opt_command(args.spirv_opt_path, args.spv_file, remaining_opts)))
