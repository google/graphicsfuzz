#!/usr/bin/env python3

# Copyright 2020 The GraphicsFuzz Project Authors
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

import sys, json, subprocess

# Note that --json-format is only available since gcov 9
cmd = ["gcov", "--json-format", "--stdout", "libVulkan.cpp.o"]

# gcov must be called in a precise location in order to find the .gcda file
workdir = "swiftshader/build-coverage/src/Vulkan/CMakeFiles/vk_swiftshader.dir"

json_string = subprocess.check_output(cmd, cwd=workdir)

files_array = json.loads(json_string)['files']
lib_vulkan_record = None

# We expect only one file_record to match
# libVulkan
for file_record in files_array:
    if "libVulkan" in file_record['file']:
        lib_vulkan_record = file_record
        break

if not lib_vulkan_record:
    print("libVulkan.cpp not found in gcov data")
    sys.exit(1)

functions = lib_vulkan_record['functions']

for function in functions:
    if function['execution_count'] == 0:
        print(function['demangled_name'])

