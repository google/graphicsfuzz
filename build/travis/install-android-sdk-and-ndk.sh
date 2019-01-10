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

echo "Installing Android SDK and NDK ${ANDROID_HOST_PLATFORM} to ${ANDROID_HOME}"

# Android SDK (ANDROID_HOST_PLATFORM must be set to linux, darwin, or windows, and ANDROID_HOME must be set to some directory).

ANDROID_TOOLS_FILENAME="sdk-tools-${ANDROID_HOST_PLATFORM}-4333796.zip"
ANDROID_NDK_FILENAME="android-ndk-r18b-${ANDROID_HOST_PLATFORM}-x86_64.zip"
ANDROID_PLATFORM_TOOLS_FILENAME="platform-tools_r28.0.1-${ANDROID_HOST_PLATFORM}.zip"

mkdir -p "${ANDROID_HOME}"
pushd "${ANDROID_HOME}"

# Android: "sdk-tools.zip" "tools":
curl -sSo "${ANDROID_TOOLS_FILENAME}" "http://dl.google.com/android/repository/${ANDROID_TOOLS_FILENAME}"
unzip -q "${ANDROID_TOOLS_FILENAME}"
rm "${ANDROID_TOOLS_FILENAME}"

# Android "android-ndk.zip" "ndk-bundle"
curl -sSo "${ANDROID_NDK_FILENAME}" "https://dl.google.com/android/repository/${ANDROID_NDK_FILENAME}"
unzip -q "${ANDROID_NDK_FILENAME}"
rm "${ANDROID_NDK_FILENAME}"
mv android-ndk-*/ ndk-bundle

# Android "platform-tools.zip" "platform-tools"
curl -sSo "${ANDROID_PLATFORM_TOOLS_FILENAME}" "https://dl.google.com/android/repository/${ANDROID_PLATFORM_TOOLS_FILENAME}"
unzip -q "${ANDROID_PLATFORM_TOOLS_FILENAME}"
rm "${ANDROID_PLATFORM_TOOLS_FILENAME}"

# Android "platforms" and "build-tools"
echo y | tools/bin/sdkmanager \
  "platforms;android-26" \
  "build-tools;28.0.2"

popd
