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

# This script downloads and builds Swiftshader with coverage enabled

#### Get the source
git clone https://github.com/google/swiftshader.git

#### Build with coverage enabled
(
  cd swiftshader
  # Known-to-work commit
  git checkout 3ad285a60d820e94f5a5c31b1e3f0cf15468fae7

  mkdir -p build-coverage
  cd build-coverage
  cmake -G Ninja -DCMAKE_BUILD_TYPE=Debug -DSWIFTSHADER_EMIT_COVERAGE=1 ..
  cmake --build . --config Debug
)
