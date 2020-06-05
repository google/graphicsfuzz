#!/bin/bash

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

# Build one of the Vulkan demos Repo
# {Sascha Willems, Google Test Applications, Khronos Samples, Imagination Demos}
#
# Run from the top level directory of the repo
# Requires that cmake and the other dependencies 
# for the given repo have to be fulfiled
#
# In particular, for the Sascha Willems demos, run
# the download_assets.py script to download the
# required assets before running this.

set -e
set -x

# This script download and builds the Khronos Vulkan Samples
# https://github.com/KhronosGroup/Vulkan-Samples

#### Check dependencies
if [ -z "$VULKAN_SDK" ]; then
    echo "VULKAN_SDK is empty, missing Vulkan SDK"
    exit 1
fi
sudo apt-get install cmake g++ xorg-dev libglu1-mesa-dev

#### Get the source
git clone --recurse-submodules https://github.com/KhronosGroup/Vulkan-Samples.git
(
    cd Vulkan-Samples
    git checkout -b gsoc 8155762d9395de224e6ab1b22ae8e5880faabd3e
)

#### Build
(
    cd Vulkan-Samples
    cmake -G "Unix Makefiles" -H. -Bbuild/linux -DCMAKE_BUILD_TYPE=Release
    cmake --build build/linux --config Release --target vulkan_samples -- -j4
)
