#!/bin/bash

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

HERE=`dirname ${BASH_SOURCE[0]}`
OS=`uname`
BIN="${HERE}/../../bin/${OS}"

temp="_tmp"

usage() {
  echo "Usage: `basename $0` shader.frag.spv"
  echo "Arguments:"
  echo "  shader.frag.spv: textual SPIR-V file"
  echo "  Expected: the companion shader.json"
  echo "  Optional: the companion shader.vert.spv"
  echo "Results: shader.{log,adb.log,ppm,png}"
  echo "Warning: this script works on temporary files named: ${temp}*"
  exit 1
}

frag=$1

if test -z ${frag}
then
  echo "Error: please provide argument"
  usage
fi

wd=`dirname $frag`
base=`basename $frag .frag.spv`

if test ! -f ${wd}/${frag}
then
  echo "Error: cannot find file: $frag"
  usage
fi

set -e
set -u

# Prepare vertex
if test ! -f ${wd}/${base}.vert.spv
then
  (
    cat << EOF
#version 310 es

layout(location=0) in highp vec4 a_position;
void main (void) {
  gl_Position = a_position;
}
EOF
  ) | ${BIN}/glslangValidator --stdin -V -S vert -o ${temp}.vert.spv
else
  ${BIN}/spirv-as ${wd}/${base}.vert.spv -o ${temp}.vert.spv
fi

# Assemble fragment
${BIN}/spirv-as ${frag} -o ${temp}.frag.spv

# Make sure the app can read/write on /sdcard/
adb shell pm grant vulkan.samples.vulkan_worker android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant vulkan.samples.vulkan_worker android.permission.WRITE_EXTERNAL_STORAGE

# Clean up
adb shell rm -rf /sdcard/graphicsfuzz
adb shell mkdir -p /sdcard/graphicsfuzz

# Put files
adb push ${temp}.frag.spv /sdcard/graphicsfuzz/test.frag.spv > /dev/null
adb push ${temp}.vert.spv /sdcard/graphicsfuzz/test.vert.spv > /dev/null
adb push ${wd}/${base}.json /sdcard/graphicsfuzz/test.json > /dev/null

# Clean temporary files
rm -f ${temp}.*

# Clear ADB logcat
adb logcat -b all -c || printf "Fail to clean all adb logs, continue\n"

# Run the app
adb shell am start -n vulkan.samples.vulkan_worker/android.app.NativeActivity

# Busy wait for DONE file to be written, or timeout
echo "Wait for app to terminate"
loop_iter=10
until adb shell test -f /sdcard/graphicsfuzz/DONE
do
  sleep 0.5
  loop_iter=$(($loop_iter - 1))
  if test $loop_iter -le 0
  then
    break
  fi
  echo -n "."
done
echo ""

# Retrieve log
adb pull /sdcard/graphicsfuzz/log.txt ${base}.log > /dev/null
adb logcat -b crash -b system -b main -b events -d > ${base}.adb.log

if adb shell test -f /sdcard/graphicsfuzz/image.ppm
then
  # Retrieve image
  adb pull /sdcard/graphicsfuzz/image.ppm ${base}.ppm > /dev/null || printf "Failed to pull PPM image\n"
  convert ${base}.ppm ${base}.png || printf "Conversion from PPM to PNG failed\n"
fi

printf "See results in:\n%s\n%s\n" ${base}.log ${base}.adb.log
if test -f ${base}.png
then
  printf "%s\n" ${base}.png
elif test -f ${base}.ppm
then
  printf "%s\n" ${base}.ppm
fi
