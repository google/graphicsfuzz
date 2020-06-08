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

json_string = subprocess.check_output(
        "pushd swiftshader/build-coverage/src/Vulkan/CMakeFiles/vk_swiftshader.dir;\
        gcov -i -t libVulkan.cpp.o;\
        popd;\
        exit 0",
        shell=True,
        executable="/bin/bash"
).decode("utf-8").split('\n')[1]

files_array = json.loads(json_string)['files']

libVulkanIndex = -1
for index in range(len(files_array)):
    if "libVulkan" in files_array[index]['file']:
        libVulkanIndex = index
        break

if libVulkanIndex == -1:
    print("libVulkan.cpp not found in gcov data")
    sys.exit(1)

functions = files_array[libVulkanIndex]['functions']

for function in functions:
    if function['execution_count'] == 0:
        print(function['demangled_name'])

