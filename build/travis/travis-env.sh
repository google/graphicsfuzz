#!/usr/bin/env bash

# Copyright 2018 The GraphicsFuzz Project Authors
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


kernel="$(uname -s)"
case "${kernel}" in
    Linux*)
        export ANDROID_HOST_PLATFORM=linux
        export GITHUB_RELEASE_TOOL_PLATFORM=linux_amd64
        ;;
    Darwin*)
        export ANDROID_HOST_PLATFORM=darwin
        export GITHUB_RELEASE_TOOL_PLATFORM=darwin_amd64
        ;;
    CYGWIN*)
        export ANDROID_HOST_PLATFORM=windows
        export GITHUB_RELEASE_TOOL_PLATFORM=windows_amd64
        ;;
    MINGW*)
        export ANDROID_HOST_PLATFORM=windows
        export GITHUB_RELEASE_TOOL_PLATFORM=windows_amd64
        ;;
    *)
        echo "Unknown platform ${kernel}."
        exit 1
esac

