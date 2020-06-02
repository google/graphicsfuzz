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

set -e
set -x

# This script runs the Google Vulkan Test Applications on swiftshader using
# xvfb. It just tests whether the apps run for a few seconds without issues.

SWIFTSHADER_ICD=`pwd`/swiftshader/build-coverage/Linux/vk_swiftshader_icd.json
if [ ! -f "${SWIFTSHADER_ICD}" ]; then
  echo "Expecting Swiftshader ICD in: ${SWIFTSHADER_ICD}"
  exit 1
fi

export VK_ICD_FILENAMES="${SWIFTSHADER_ICD}"

APP_DIR=vulkan_test_applications/build/bin
if [ ! -d "${APP_DIR}" ]; then
  echo "Expecting Vulkan test applications in: ${APP_DIR}"
  exit 1
fi

# Stop being verbose and authorize non-zero return code
set +e
set +x

for APP in ${APP_DIR}/* ; do
  if [ -x "${APP}" ] ; then
    APPNAME=`basename ${APP}`
    #xvfb-run -e "${APPNAME}-xvfb.log" -a timeout --preserve-status -s INT -k 1 3 "${APP}" > "${APPNAME}.log" 2>&1
    xvfb-run -e "${APPNAME}-xvfb.log" -a timeout -s INT -k 1 3 "${APP}" > "${APPNAME}.log" 2>&1
    RETURN_CODE="${?}"
    echo "${APPNAME} ${RETURN_CODE}"
  fi
done
