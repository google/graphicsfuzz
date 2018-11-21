
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
  apt-get -y install software-properties-common && \
  add-apt-repository ppa:orangain/opencv && \
  apt-get update && \
  apt-get -y install openjdk-8-jdk python python-opencv ipython python-six && \
  apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY . /data/server/

RUN \
  mkdir -p /data/server && \
  mkdir -p /data/work && \
  cp -R /data/server/sample-shadersets /data/work/shadersets && \
  chmod -R uga+wr /data/work

EXPOSE 8080

WORKDIR /data/work

CMD [\
  "../server/docker/scripts/umask-wrapper.sh", \
  "java", \
  "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-Xmx5g", "-Xms1g", \
  "-ea", \
  "-jar", "../server/jar/server-1.0.jar"]
