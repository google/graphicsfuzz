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

set -e
set -x

# This script downloads and builds SaschaWillems Vulkan demos
# https://github.com/SaschaWillems/Vulkan

#### Check dependencies
if [ -z "$VULKAN_SDK" ]; then
  echo "VULKAN_SDK is empty, missing Vulkan SDK"
  exit 1
fi

# TODO: check presence of: libassimp-dev

#### Get the source
# The default repo name is "Vulkan", let's be more clear and use
# SaschaWillems_Vulkan to mirror the github name.
git clone https://github.com/SaschaWillems/Vulkan SaschaWillems_Vulkan
(
  cd SaschaWillems_Vulkan
  # Known-to-work commit
  git checkout -b gsoc 4818f85916bf88c1ca8c2ed1a46e0e758651489e

  git submodule update --init --recursive

  python3 download_assets.py
)


#### Build
(
  cd SaschaWillems_Vulkan
  mkdir build
  cd build
  cmake -G Ninja -DCMAKE_BUILD_TYPE=Release ..
  cmake --build . --config Release
)
