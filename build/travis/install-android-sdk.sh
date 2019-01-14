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

export ANDROID_HOME="$(pwd)"

echo "Installing Android SDK ${ANDROID_HOST_PLATFORM} (linux, darwin, or windows) to ${ANDROID_HOME}"

ANDROID_TOOLS_FILENAME="sdk-tools-${ANDROID_HOST_PLATFORM}-4333796.zip"
ANDROID_PLATFORM_TOOLS_FILENAME="platform-tools_r28.0.1-${ANDROID_HOST_PLATFORM}.zip"

if test ! -f "${ANDROID_TOOLS_FILENAME}.touch"; then
  # Android: "sdk-tools.zip" "tools":
  rm -rf tools
  curl -sSo "${ANDROID_TOOLS_FILENAME}" "http://dl.google.com/android/repository/${ANDROID_TOOLS_FILENAME}"
  unzip -q "${ANDROID_TOOLS_FILENAME}"
  rm "${ANDROID_TOOLS_FILENAME}"
  test -d tools
  touch "${ANDROID_TOOLS_FILENAME}.touch"
fi

if test ! -f "${ANDROID_PLATFORM_TOOLS_FILENAME}.touch"; then
  # Android "platform-tools.zip" "platform-tools"
  rm -rf platform-tools
  curl -sSo "${ANDROID_PLATFORM_TOOLS_FILENAME}" "https://dl.google.com/android/repository/${ANDROID_PLATFORM_TOOLS_FILENAME}"
  unzip -q "${ANDROID_PLATFORM_TOOLS_FILENAME}"
  rm "${ANDROID_PLATFORM_TOOLS_FILENAME}"
  test -d platform-tools
  touch "${ANDROID_PLATFORM_TOOLS_FILENAME}.touch"
fi

if test ! -f "android-26-build-tools-28.0.2.touch"; then
  # Android "platforms" and "build-tools"
  echo y | tools/bin/sdkmanager \
    "platforms;android-26" \
    "build-tools;28.0.2"
  touch "android-26-build-tools-28.0.2.touch"
fi
