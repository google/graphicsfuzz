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

echo "Installing Android NDK ${ANDROID_HOST_PLATFORM} (linux, darwin, or windows) ..."

ANDROID_TOOLS_FILENAME="sdk-tools-${ANDROID_HOST_PLATFORM}-4333796.zip"
ANDROID_NDK_FILENAME="android-ndk-r18b-${ANDROID_HOST_PLATFORM}-x86_64.zip"
ANDROID_PLATFORM_TOOLS_FILENAME="platform-tools_r28.0.1-${ANDROID_HOST_PLATFORM}.zip"

export ANDROID_NDK_HOME="$(pwd)/android-ndk-r18b"

echo "... to ${ANDROID_NDK_HOME}"

if test ! -d "${ANDROID_NDK_HOME}"; then
  # Android "android-ndk.zip" "ndk-bundle"
  curl -sSo "${ANDROID_NDK_FILENAME}" "https://dl.google.com/android/repository/${ANDROID_NDK_FILENAME}"
  unzip -q "${ANDROID_NDK_FILENAME}"
  rm "${ANDROID_NDK_FILENAME}"
  test -d "${ANDROID_NDK_HOME}"
fi
