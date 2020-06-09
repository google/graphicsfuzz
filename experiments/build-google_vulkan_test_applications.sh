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

# This script download and builds Google Vulkan test applications
# https://github.com/google/vulkan_test_applications

#### Check dependencies
if [ -z "$VULKAN_SDK" ]; then
  echo "VULKAN_SDK is empty, missing Vulkan SDK"
  exit 1
fi

#### Get the source
git clone https://github.com/google/vulkan_test_applications
(
  cd vulkan_test_applications
  # Known-to-work commit
  git checkout -b gsoc b8789db91ea470f70fce73746b051a06a055824f

  git submodule update --init --recursive
)

#### Build
(
  cd vulkan_test_applications
  mkdir build
  cd build
  cmake -G Ninja -DCMAKE_BUILD_TYPE=Release ..
  cmake --build . --config Release
)
