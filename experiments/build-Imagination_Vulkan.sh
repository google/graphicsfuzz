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

# Build the Imagination Demos Repo

set -e
set -x

# This script download and builds the Imagination Vulkan demos 
# https://github.com/powervr-graphics/Native_SDK.git

#### Check dependencies
if [ -z "$VULKAN_SDK" ]; then
    echo "VULKAN_SDK is empty, missing Vulkan SDK"
    exit 1
fi

#### Get the source
git clone https://github.com/powervr-graphics/Native_SDK.git Imagination-Vulkan-Samples
(
    cd Imagination-Vulkan-Samples
    git checkout -b gsoc 0a5b48fd1f4ad251f5b4f0e07c46744c4841255b 
)

#### Build
(
    cd Imagination-Vulkan-Samples
    mkdir build
    cd build
    cmake -G Ninja -DPVR_WINDOW_SYSTEM=X11 -DCMAKE_BUILD_TYPE=Release ..
    cmake --build . --config Release
)
