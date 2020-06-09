#!/usr/bin/env bash

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

# Build the Khronos Samples Repo

set -e
set -x

# This script download and builds the Khronos Vulkan Samples
# https://github.com/KhronosGroup/Vulkan-Samples

#### Check dependencies
if [ -z "$VULKAN_SDK" ]; then
    echo "VULKAN_SDK is empty, missing Vulkan SDK"
    exit 1
fi

#### Get the source
git clone https://github.com/KhronosGroup/Vulkan-Samples.git
(
    cd Vulkan-Samples
    git checkout -b gsoc 8155762d9395de224e6ab1b22ae8e5880faabd3e

    # Update submodules after checking out the required commit
    git submodule update --init --recursive
)

#### Build
(
    cd Vulkan-Samples
    cmake -G Ninja -H. -Bbuild/linux -DCMAKE_BUILD_TYPE=Release
    cmake --build build/linux --config Release --target vulkan_samples
)
