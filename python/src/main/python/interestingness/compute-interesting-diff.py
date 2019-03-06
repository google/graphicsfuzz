#!/usr/bin/env python3

# Copyright 2019 The GraphicsFuzz Project Authors
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

import json
import os
import subprocess
import sys
import shutil

REFERENCE_RESULTS = os.path.dirname(os.path.realpath(__file__)) + os.sep + 'reference.info.json'  # Edit according to your needs
WORKER_NAME = 'your-worker'  # Edit according to your needs
SERVER_URL = 'http://localhost:8080'  # Edit according to your needs

# The interestingness test is given a shader job .json file as its single argument
variant_shader_job = sys.argv[1]

if not os.path.isfile(REFERENCE_RESULTS):
    sys.stderr.write('Not interesting: file ' + REFERENCE_RESULTS + ' not found; this is likely fatal.')
    sys.exit(1)

cmd = [shutil.which('run-shader-family'), '--worker', WORKER_NAME, '--server', SERVER_URL, variant_shader_job]
proc = subprocess.run(cmd)

if proc.returncode != 0:
    sys.stderr.write('Not interesting: running the shader job failed\n')
    sys.exit(2)

variant_results_filename = os.path.splitext(os.path.basename(variant_shader_job))[0] + '.info.json'

if not os.path.isfile(variant_results_filename):
    sys.stderr.write('Not interesting: no results file\n')
    sys.exit(3)

with open(variant_results_filename, 'r') as f:
    json_variant_results = json.load(f)
    if json_variant_results['status'] != 'SUCCESS':
        sys.stderr.write('Not interesting: result was ' + json_variant_results['status'] + '\n')
        sys.exit(4)

cmd = [shutil.which('inspect-compute-results'), 'exactdiff', REFERENCE_RESULTS, variant_results_filename]
proc = subprocess.run(cmd)

if proc.returncode == 0:
    sys.stderr.write('Not interesting: results match\n')
    sys.exit(5)

sys.stderr.write('Interesting!\n')
sys.exit(0)
