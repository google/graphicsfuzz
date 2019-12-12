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

MAVEN_VERSION="3.6.0"
MAVEN_FILE="apache-maven-${MAVEN_VERSION}-bin.zip"
MAVEN_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/${MAVEN_FILE}"

echo "Installing maven ${MAVEN_VERSION} to $(pwd)/apache-maven-${MAVEN_VERSION}"

if test ! -f "${MAVEN_FILE}.touch"; then
  curl -sSo "${MAVEN_FILE}" "${MAVEN_URL}"
  unzip -q "${MAVEN_FILE}"
  rm "${MAVEN_FILE}"
  test -d "$(pwd)/apache-maven-${MAVEN_VERSION}/bin"
  touch "${MAVEN_FILE}.touch"
fi

export PATH="$(pwd)/apache-maven-${MAVEN_VERSION}/bin:${PATH}"
