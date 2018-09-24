
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

FROM ubuntu:16.04

RUN \
  apt-get update && \
  apt-get -y install \
    openjdk-8-jdk python maven \
    git golang wget unzip && \
  apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*


ENV \
  ANDROID_TOOLS_FILENAME=tools_r25.2.3-linux.zip \
  ANDROID_API_LEVELS="platforms;android-23" \
  ANDROID_BUILD_TOOLS_VERSION=25.0.0 \
  ANDROID_HOME=/opt/android-sdk-linux

ENV \
  ANDROID_TOOLS_URL=http://dl.google.com/android/repository/${ANDROID_TOOLS_FILENAME} \
  PATH=${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/platform-tools

RUN \
  mkdir -p ${ANDROID_HOME} && \
  cd ${ANDROID_HOME} && \
  wget -q ${ANDROID_TOOLS_URL} && \
  unzip ${ANDROID_TOOLS_FILENAME} && \
  rm ${ANDROID_TOOLS_FILENAME} && \
  echo y | sdkmanager "extras;android;m2repository" "tools" "platform-tools" "${ANDROID_API_LEVELS}" "build-tools;${ANDROID_BUILD_TOOLS_VERSION}"

ENV \
  GOPATH=/data/gopath \
  PATH=/data/gopath/bin:${PATH}

COPY id_rsa id_rsa.pub TOKEN ci.py umask-wrapper.sh /data/

WORKDIR /data

RUN \
  mkdir -p /root/.ssh/ && \
  bash -c 'echo -e "Host github.com\n\tStrictHostKeyChecking no\n">/root/.ssh/config' && \
  chmod 0600 id_rsa && \
  cp id_rsa id_rsa.pub /root/.ssh/ && \
  mkdir -p ${GOPATH} && \
  go get github.com/c4milo/github-release


#CMD ["bash"]
CMD ["./umask-wrapper.sh", "./ci.py"]