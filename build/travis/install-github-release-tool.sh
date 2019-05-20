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

set -x
set -e
set -u

echo "Installing github-release ${GITHUB_RELEASE_TOOL_PLATFORM} (linux_amd64, darwin_amd64, windows_amd64, etc.) tool to $(pwd)."

GITHUB_RELEASE_TOOL_USER="paulthomson"
GITHUB_RELEASE_TOOL_VERSION="v1.1.0.1"
GITHUB_RELEASE_TOOL_FILE="github-release_${GITHUB_RELEASE_TOOL_VERSION}_${GITHUB_RELEASE_TOOL_PLATFORM}.tar.gz"

if test ! -f "${GITHUB_RELEASE_TOOL_FILE}.touch"; then
  curl -Lo "${GITHUB_RELEASE_TOOL_FILE}" \
    "https://github.com/${GITHUB_RELEASE_TOOL_USER}/github-release/releases/download/${GITHUB_RELEASE_TOOL_VERSION}/${GITHUB_RELEASE_TOOL_FILE}"
  tar xf "${GITHUB_RELEASE_TOOL_FILE}"
  rm "${GITHUB_RELEASE_TOOL_FILE}"
  touch "${GITHUB_RELEASE_TOOL_FILE}.touch"
fi

export PATH="$(pwd):${PATH}"
