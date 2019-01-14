
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

FROM ubuntu:14.04

ENV JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64" PATH="/data/maven/bin:${PATH}" PYTHON_GF="python3.5" MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

RUN \
  apt-get update -q && \
  apt-get install -y software-properties-common && \
  add-apt-repository ppa:deadsnakes/ppa -y && \
  add-apt-repository ppa:openjdk-r/ppa -y && \
  apt-get update -q && \
  apt-get -y install openjdk-8-jdk python3.5 git unzip curl ca-certificates-java && \
  apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* && \
  update-java-alternatives --set java-1.8.0-openjdk-amd64 && \
  mkdir -p /data && \
  cd /data && \
  curl -sSo apache-maven-3.6.0-bin.zip https://www-us.apache.org/dist/maven/maven-3/3.6.0/binaries/apache-maven-3.6.0-bin.zip && \
  unzip apache-maven-3.6.0-bin.zip && \
  mv apache-maven-3.6.0 maven && \
  git clone --recursive https://github.com/google/graphicsfuzz.git && \
  cd graphicsfuzz && \
  git checkout master && \
  build/travis/travis-release-graphicsfuzz.sh && \
  cd .. && \
  rm -rf graphicsfuzz
