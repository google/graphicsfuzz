#!/usr/bin/env python3

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
    sys.exit(0)

functions = files_array[libVulkanIndex]['functions']

for function in functions:
    if function['execution_count'] == 0:
        print(function['demangled_name'])

