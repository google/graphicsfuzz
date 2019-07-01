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

import os
import shutil
import argparse


# From glsl-to-spv-worker
def remove(f):
    if os.path.isdir(f):
        shutil.rmtree(f)
    elif os.path.isfile(f):
        os.remove(f)


def main():
    description = (
        'Uses the piglit GLES3 shader runner to render shader jobs.'
    )

    parser = argparse.ArgumentParser(description=description)

    # Required
    parser.add_argument(
        'worker_name',
        help='The name that will refer to this worker.'
    )

    # Optional
    parser.add_argument(
        '--server',
        default='http://localhost:8080',
        help='Server URL to connect to (default: http://localhost:8080 )'
    )

    args = parser.parse_args()

    print('Worker: ' + args.worker_name)
    server = args.server + '/request'
    print('server: ' + server)

    # Get worker info
    worker_info_file = 'worker_info.json'
    remove(worker_info_file)

    worker_info_json_string = '{}'




